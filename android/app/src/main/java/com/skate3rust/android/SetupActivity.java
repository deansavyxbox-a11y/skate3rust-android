package com.skate3rust.android;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.IOException;

/** Setup gate shown before the native gameplay process initializes. */
public final class SetupActivity extends Activity {
    private static final int PICK_ZIP=1,PICK_ISO=2;
    private InstallationStore store;
    private TextView status,selected,notice;
    private Uri pendingUri;
    private int pendingType;
    private String pendingName;
    private Button importButton,playButton;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener jobListener=(prefs,key)->{
        if(ImportProgressText.shouldRefresh(key))refreshJobState();
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(SetupRoute.destination(NativeSession.isActive())==SetupRoute.Destination.GAME) {bringGameForward();return;}
        try {store=new InstallationStore(getFilesDir());store.recover();}
        catch(IOException error) {showRecoveryError(error);return;}
        buildScreen();
        if(state!=null&&state.containsKey("pendingUri")) {
            pendingUri=Uri.parse(state.getString("pendingUri"));pendingType=state.getInt("pendingType");pendingName=state.getString("pendingName");showSelection();
        }
    }
    private void bringGameForward() {
        startActivity(new Intent(this,GameActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION));finish();
    }
    private void buildScreen() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setGravity(Gravity.CENTER);page.setPadding(dp(28),dp(20),dp(28),dp(20));page.setBackgroundColor(0xff09151f);
        TextView title=new TextView(this);title.setText("SKATE 3 RUST");title.setTextSize(26);title.setTextColor(0xffff592d);page.addView(title,wrap());
        status=new TextView(this);status.setTextColor(0xffeeeeee);status.setTextSize(16);status.setGravity(Gravity.CENTER);page.addView(status,wrap());
        playButton=button("PLAY",()->startActivity(new Intent(this,LaunchActivity.class)));page.addView(playButton,buttonLayout());
        Button zip=button("IMPORT PREPARED ASSETS (.ZIP)",()->pick(PICK_ZIP));page.addView(zip,buttonLayout());
        Button iso=button("IMPORT XBOX 360 ISO",()->pick(PICK_ISO));page.addView(iso,buttonLayout());
        selected=new TextView(this);selected.setTextColor(0xffdddddd);selected.setGravity(Gravity.CENTER);page.addView(selected,wrap());
        importButton=button("CONTINUE IMPORT",this::startImport);importButton.setEnabled(false);page.addView(importButton,buttonLayout());
        Button restore=button("RESTORE PREVIOUS ASSETS",this::restore);restore.setEnabled(new File(getFilesDir(),"installation.previous/complete.json").isFile());page.addView(restore,buttonLayout());
        notice=new TextView(this);notice.setTextColor(0xffffcf75);notice.setGravity(Gravity.CENTER);page.addView(notice,wrap());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);setContentView(scroll);refreshStatus();
    }
    private void refreshStatus() {
        boolean playable=store.isPlayable();
        String mapSummary="";
        if(playable)try{mapSummary=" · "+String.join(", ",AssetManifest.read(new File(getFilesDir(),"installation")).maps);}catch(IOException ignored){}
        status.setText(playable?"Assets ready"+mapSummary:"No playable asset installation yet. Choose one of the import options below.");
        playButton.setEnabled(playable);
    }
    private void pick(int type) {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        // Document providers assign several different MIME types to .iso files.
        // Leave ISO browsing unrestricted; the service checks the selected extension and disc format.
        if(type==PICK_ZIP)intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,type);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);ImportSource.retainGrant(this,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        pendingUri=data.getData();pendingType=requestCode;pendingName=ImportSource.displayName(this,pendingUri);showSelection();
    }
    private void showSelection() {
        selected.setText("Selected: "+pendingName+"\nOriginal file will remain untouched.");
        importButton.setEnabled(pendingType==PICK_ZIP||pendingType==PICK_ISO);
        showNotice(pendingType==PICK_ZIP?"Prepared asset ZIP. Game data is copied into private app storage.":"The selected Xbox 360 ISO will be copied privately, converted on this phone, then validated before installation.");
    }
    private void startImport() {
        if(pendingUri==null)return;
        Intent intent=new Intent(this,ImportService.class).setAction(ImportService.ACTION_IMPORT).setData(pendingUri)
            .putExtra(ImportService.EXTRA_KIND,pendingType==PICK_ZIP?ImportService.KIND_ZIP:ImportService.KIND_ISO)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);importButton.setEnabled(false);showNotice("Import running. You can leave this screen while Android continues the job.");}
        catch(RuntimeException e){showNotice("Could not start import: "+e.getMessage());}
    }
    private void restore() {
        try{store.restorePrevious();notice.setText("Previous asset installation restored.");refreshStatus();}
        catch(IOException e){showNotice("Could not restore previous assets: "+e.getMessage());}
    }
    private void showRecoveryError(IOException error) {
        TextView message=new TextView(this);message.setText("Asset recovery needs attention. Your previous files have been kept.\n\n"+error.getMessage());message.setTextColor(0xffffffff);message.setGravity(Gravity.CENTER);message.setPadding(dp(24),dp(24),dp(24),dp(24));setContentView(message);
    }
    @Override protected void onResume(){super.onResume();getSharedPreferences(ImportService.PREFS,MODE_PRIVATE).registerOnSharedPreferenceChangeListener(jobListener);if(store!=null)refreshJobState();}
    @Override protected void onPause(){getSharedPreferences(ImportService.PREFS,MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(jobListener);super.onPause();}
    private void refreshJobState(){
        if(store==null||notice==null)return;
        android.content.SharedPreferences prefs=getSharedPreferences(ImportService.PREFS,MODE_PRIVATE);String value=prefs.getString("phase","");
        if(value.isEmpty())return;
        ImportModel.Phase phase;try{phase=ImportModel.Phase.valueOf(value);}catch(IllegalArgumentException e){return;}
        phase=ImportJobState.visiblePhase(phase,ImportService.isImportRunning());
        if(phase==ImportModel.Phase.COMPLETE){showNotice("Import complete. Assets are ready to play.");refreshStatus();return;}
        if(phase==ImportModel.Phase.INTERRUPTED){
            String uri=prefs.getString("uri","");String kind=prefs.getString("kind","");
            refreshStatus();
            if(!uri.isEmpty()&&(ImportService.KIND_ZIP.equals(kind)||ImportService.KIND_ISO.equals(kind))){pendingUri=Uri.parse(uri);pendingType=ImportService.KIND_ZIP.equals(kind)?PICK_ZIP:PICK_ISO;pendingName=ImportSource.displayName(this,pendingUri);selected.setText("Import stopped before completion. Retry the selected file when ready.");importButton.setText("RETRY IMPORT");importButton.setEnabled(true);}
            showNotice("Android stopped the import. The previous installation remains available.");return;
        }
        if(!ImportJobState.terminal(phase)) {
            playButton.setEnabled(false);importButton.setEnabled(false);
            long done=prefs.getLong("completed",0),total=prefs.getLong("total",-1);
            showNotice(ImportProgressText.render(phase,done,total,prefs.getString("item",""),prefs.getString("map_progress","")));
        }
        else if(phase==ImportModel.Phase.FAILED||phase==ImportModel.Phase.CANCELLED){showNotice(prefs.getString("message","Import did not finish."));refreshStatus();importButton.setEnabled(pendingUri!=null&&(pendingType==PICK_ZIP||pendingType==PICK_ISO));}
    }
    private void showNotice(String text){if(notice!=null)notice.setText(text);}
    private Button button(String text,Runnable action) {Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());return b;}
    private LinearLayout.LayoutParams buttonLayout(){return new LinearLayout.LayoutParams(-1,dp(54));}
    private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT);}
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onSaveInstanceState(Bundle state){if(pendingUri!=null){state.putString("pendingUri",pendingUri.toString());state.putInt("pendingType",pendingType);state.putString("pendingName",pendingName);}super.onSaveInstanceState(state);}
}

