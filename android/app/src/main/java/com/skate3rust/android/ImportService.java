package com.skate3rust.android;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-started background importer. It never exposes source contents in notifications or logs. */
public final class ImportService extends Service {
    static final String ACTION_IMPORT="com.skate3rust.android.IMPORT",ACTION_CANCEL="com.skate3rust.android.CANCEL_IMPORT";
    static final String EXTRA_URI="source_uri",EXTRA_KIND="source_kind",KIND_ZIP="zip",KIND_ISO="iso";
    static final String PREFS="asset_import_job";
    private static final String CHANNEL="asset_import",PREF_ID="id",PREF_PHASE="phase",PREF_COMPLETED="completed",PREF_TOTAL="total",PREF_ITEM="item",PREF_MAP_PROGRESS="map_progress",PREF_MESSAGE="message",PREF_URI="uri",PREF_KIND="kind",PREF_ABANDONED_ID="abandoned_id";
    private static final int NOTIFICATION=6412;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final AtomicBoolean storageInsufficient=new AtomicBoolean();
    private static volatile boolean workerRunning;
    static boolean isImportRunning(){return workerRunning;}
    private ImportJobState state=new ImportJobState();
    private String mapProgress="";
    private final ImportProgressThrottle progressThrottle=new ImportProgressThrottle();

    @Override public void onCreate(){super.onCreate();createChannel();restoreState();}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&ACTION_CANCEL.equals(intent.getAction())) {cancelled.set(true);return START_NOT_STICKY;}
        if(intent==null||!ACTION_IMPORT.equals(intent.getAction())){stopSelf(startId);return START_NOT_STICKY;}
        startForegroundCompat(notification("Preparing import",0,-1));
        Uri uri=intent.getData();String kind=intent.getStringExtra(EXTRA_KIND);
        if(uri==null||(!KIND_ZIP.equals(kind)&&!KIND_ISO.equals(kind))){state.start(UUID.randomUUID().toString(),ImportModel.Phase.INSPECTING);state.finish(ImportModel.Phase.FAILED,"The selected file could not be reopened.");persist(null,null);updateNotification();stopForeground(STOP_FOREGROUND_DETACH);stopSelf(startId);return START_NOT_STICKY;}
        String interruptedId=getSharedPreferences(PREFS,MODE_PRIVATE).getString(PREF_ABANDONED_ID,state.snapshot().id);
        String id=UUID.randomUUID().toString();if(!state.start(id,ImportModel.Phase.INSPECTING)){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);return START_NOT_STICKY;}
        mapProgress="";workerRunning=true;
        // Retain the abandoned id separately across cancellation/failure and process death.
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(PREF_ABANDONED_ID,interruptedId)
            .putString(PREF_URI,uri.toString()).putString(PREF_KIND,kind).commit();persist(null,null);
        WORKER.execute(()->runImport(uri,kind,startId,interruptedId));return START_NOT_STICKY;
    }
    private void runImport(Uri uri,String kind,int startId,String interruptedId) {
        ImportSource.Handle source=null;File stage=null;
        try {
            checkCancelled();
            InstallationStore store=new InstallationStore(getFilesDir());store.recover();
            store.discardInterruptedStaging(interruptedId);
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().remove(PREF_ABANDONED_ID).commit();
            source=KIND_ISO.equals(kind)?ImportSource.openIso(this,uri):ImportSource.open(this,uri);
            state.report(ImportModel.Phase.INSPECTING,0,source.size,"Checking prepared asset package");persist(null,null);updateNotification();
            stage=store.newStaging(state.snapshot().id);
            final File target=stage;
            ImportModel.Progress progress=(phase,completed,total,item)->{
                state.report(phase,completed,total,item);
                if(progressThrottle.shouldEmit(phase,SystemClock.elapsedRealtime())) {
                    if(getFilesDir().getUsableSpace()<64L*1024*1024)throw new java.io.UncheckedIOException(new IOException("Not enough free app storage to safely finish this import."));
                    persist(null,null);updateNotification();
                }
            };
            AssetManifest manifest;
            if(KIND_ISO.equals(kind)) {
                if(!ImportSource.displayName(this,uri).toLowerCase(java.util.Locale.ROOT).endsWith(".iso"))
                    throw new IOException("Select an Xbox 360 .iso image.");
                File work=new File(stage,"conversion");if(!work.mkdirs())throw new IOException("Could not prepare private conversion storage.");
                File copiedIso=null;
                if(!source.isSeekable()) {
                    copiedIso=new File(work,"source.iso");
                    copyIso(source,copiedIso);
                }
                File disc=new File(work,"disc");if(!disc.mkdirs())throw new IOException("Could not prepare private disc extraction storage.");
                state.report(ImportModel.Phase.CONVERTING,0,-1,source.isSeekable()?"Reading Xbox disc directly; no ISO copy needed":"Reading copied Xbox disc");persist(null,null);updateNotification();
                NativeAssetValidator.Progress observer=new NativeAssetValidator.Progress(){
                    public boolean isCancelled(){
                        if(getFilesDir().getUsableSpace()<64L*1024*1024){storageInsufficient.set(true);return true;}
                        return cancelled.get()||Thread.currentThread().isInterrupted();
                    }
                    public void onProgress(long completed,long total,String item){
                        state.report(ImportModel.Phase.CONVERTING,completed,total,"Extracting owned disc files");
                        if(progressThrottle.shouldEmit(ImportModel.Phase.CONVERTING,SystemClock.elapsedRealtime())){persist(null,null);updateNotification();}
                    }
                };
                if(source.isSeekable())NativeAssetValidator.nativeExtractIsoFd(source.fd(),disc.getAbsolutePath(),observer);
                else NativeAssetValidator.nativeExtractIso(copiedIso.getAbsolutePath(),disc.getAbsolutePath(),observer);
                checkCancelled();
                if(copiedIso!=null)java.nio.file.Files.delete(copiedIso.toPath());
                IsoAssetConverter.convert(this,disc,target,work,new IsoAssetConverter.Callbacks(){
                    public void report(String message){mapProgress=ImportProgressText.mapContext(mapProgress,message);state.report(ImportModel.Phase.CONVERTING,0,-1,message);persist(null,null);updateNotification();}
                    public void checkCancelled()throws IOException{ImportService.this.checkCancelled();}
                });
                checkCancelled();cleanup(work);
                state.report(ImportModel.Phase.VALIDATING,0,-1,"Checking converted game assets");persist(null,null);updateNotification();
                manifest=AssetManifest.create(stage,progress,this::checkCancelled);
                NativeAssetValidator.nativeValidate(stage.getAbsolutePath());
            } else {
                manifest=PreparedZipImporter.importFrom(source.stream,stage,progress,this::checkCancelled);
                NativeAssetValidator.nativeValidate(stage.getAbsolutePath());
            }
            checkCancelled();state.report(ImportModel.Phase.VALIDATING,manifest.totalBytes,manifest.totalBytes,"Checking installed game files");persist(null,null);updateNotification();
            store.publish(stage,progress,this::checkCancelled);
            state.finish(ImportModel.Phase.COMPLETE,"Assets installed. You can play now.");persist(null,null);
        } catch(java.io.InterruptedIOException e) {
            cleanup(stage);state.finish(ImportModel.Phase.CANCELLED,"Import cancelled. Your previous installation is unchanged.");persist(null,null);
        } catch(Throwable e) {
            cleanup(stage);if(storageInsufficient.get())state.finish(ImportModel.Phase.FAILED,"Not enough free app storage to finish this import. Your previous installation is unchanged.");
            else if(cancelled.get())state.finish(ImportModel.Phase.CANCELLED,"Import cancelled. Your previous installation is unchanged.");
            else {String message=e.getMessage()==null?"Import failed.":e.getMessage();state.finish(ImportModel.Phase.FAILED,message);}persist(null,null);
        } finally {
            if(source!=null)try{source.close();}catch(IOException ignored){}
            workerRunning=false;releaseGrant(uri);updateNotification();stopForeground(STOP_FOREGROUND_DETACH);stopSelf(startId);
        }
    }
    private void checkCancelled()throws InterruptedIOException {if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Import cancelled");}
    private void copyIso(ImportSource.Handle source,File target)throws IOException {
        if(source.size==0)throw new IOException("The selected ISO is empty.");
        byte[] buffer=new byte[256*1024];long copied=0;int n;
        try(FileOutputStream output=new FileOutputStream(target)) {
            while((n=source.stream.read(buffer))!=-1) {
                checkCancelled();copied=Math.addExact(copied,n);
                if(copied>AssetManifest.MAX_BYTES)throw new IOException("The selected ISO exceeds the supported 32 GiB limit.");
                if(getFilesDir().getUsableSpace()<n+128L*1024*1024)throw new IOException("Not enough free app storage to safely copy the selected ISO.");
                output.write(buffer,0,n);
                if((copied&0x3fffff)<n){state.report(ImportModel.Phase.CONVERTING,copied,source.size,"Copying the selected ISO into private storage");persist(null,null);updateNotification();}
            }
            output.getFD().sync();
        } catch(ArithmeticException overflow){throw new IOException("The selected ISO is too large.",overflow);}
        if(copied==0||(source.size>=0&&copied!=source.size))throw new IOException("The selected ISO was truncated while being read.");
    }
    private void cleanup(File stage){if(stage==null||!stage.exists())return;try{java.nio.file.Files.walk(stage.toPath()).sorted(java.util.Comparator.reverseOrder()).forEach(path->{try{java.nio.file.Files.deleteIfExists(path);}catch(IOException ignored){}});}catch(IOException ignored){}}
    private void releaseGrant(Uri uri){try{getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException|IllegalArgumentException ignored){}}
    private void restoreState(){android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);String id=p.getString(PREF_ID,"");if(id.isEmpty())return;ImportModel.Phase phase;try{phase=ImportModel.Phase.valueOf(p.getString(PREF_PHASE,"INTERRUPTED"));}catch(IllegalArgumentException e){phase=ImportModel.Phase.INTERRUPTED;}ImportJobState.Snapshot snap=ImportJobState.restore(id,phase,p.getLong(PREF_COMPLETED,0),p.getLong(PREF_TOTAL,-1),p.getString(PREF_ITEM,""));state.start(snap.id,snap.phase);state.finish(snap.phase,snap.message);if(!ImportJobState.terminal(phase))persist(null,null);}
    private void persist(String uri,String kind){ImportJobState.Snapshot s=state.snapshot();android.content.SharedPreferences.Editor e=getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(PREF_ID,s.id).putString(PREF_PHASE,s.phase.name()).putLong(PREF_COMPLETED,s.completed).putLong(PREF_TOTAL,s.total).putString(PREF_ITEM,s.item).putString(PREF_MAP_PROGRESS,mapProgress).putString(PREF_MESSAGE,s.message);if(uri!=null)e.putString(PREF_URI,uri);if(kind!=null)e.putString(PREF_KIND,kind);e.commit();}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Asset import",NotificationManager.IMPORTANCE_LOW));}}
    private void startForegroundCompat(Notification notification){if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(NOTIFICATION,notification);}
    private Notification notification(String text,long completed,long total){ImportJobState.Snapshot s=state.snapshot();Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Skate 3 Rust assets").setContentText(text).setOngoing(s.running()).setOnlyAlertOnce(true);if(total>0)b.setProgress(100,(int)Math.min(100,completed*100/total),false);if(s.running()){Intent cancel=new Intent(this,ImportService.class).setAction(ACTION_CANCEL);b.addAction(android.R.drawable.ic_menu_close_clear_cancel,"Cancel",PendingIntent.getService(this,NOTIFICATION,cancel,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));}return b.build();}
    private void updateNotification(){ImportJobState.Snapshot s=state.snapshot();String message=s.message.isEmpty()?s.phase.name().toLowerCase().replace('_',' '):s.message;((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION,notification(message,s.completed,s.total));}
    @Override public void onTimeout(int startId,int foregroundServiceType){cancelled.set(true);stopSelf(startId);}
    @Override public IBinder onBind(Intent intent){return null;}
}
