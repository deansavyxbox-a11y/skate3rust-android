package com.skate3rust.android;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Owns publication; never mutates the active generation until input is verified. */
final class InstallationStore {
    interface FaultHook {void after(String boundary)throws IOException;}
    private final File root,live,previous,journal;
    private final FaultHook faults;
    InstallationStore(File root)throws IOException{this(root,boundary->{});}
    InstallationStore(File root,FaultHook faults)throws IOException {
        this.root=root.getCanonicalFile();Files.createDirectories(this.root.toPath());
        live=new File(this.root,"installation");previous=new File(this.root,"installation.previous");journal=new File(this.root,"import-publication.properties");this.faults=faults;
    }
    File newStaging(String id)throws IOException {
        if(!id.matches("[A-Za-z0-9_-]{1,80}"))throw new IOException("Invalid import identifier");
        File stage=new File(root,"import-"+id+".pending");if(!stage.mkdir())throw new IOException("Import staging already exists");return stage;
    }
    void recover()throws IOException {try(LockHandle ignored=lock()){recoverLocked();}}
    /** Called by the serialized worker before allocating a retry's new staging directory. */
    void discardInterruptedStaging(String id)throws IOException {
        if(id==null||id.isEmpty())return;
        if(!id.matches("[A-Za-z0-9_-]{1,80}"))throw new IOException("Invalid interrupted import identifier");
        try(LockHandle ignored=lock()) {
            // A publication journal may still own this stage: finish recovery first.
            recoverLocked();
            File abandoned=new File(root,"import-"+id+".pending");
            if(!abandoned.exists())return;
            if(Files.isSymbolicLink(abandoned.toPath())||!abandoned.getCanonicalFile().getParentFile().equals(root))
                throw new IOException("Interrupted staging escaped app storage");
            // Files.walk does not follow symbolic links; never traverse outside the owned directory.
            try(var paths=Files.walk(abandoned.toPath())) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(path);
            }
        }
    }
    boolean isPlayable(){return playable(live);}
    List<String> availableStartingMaps()throws IOException {
        AssetManifest manifest=AssetManifest.read(live);manifest.verifyRuntime(live);
        return manifest.maps;
    }
    String currentStartingMap()throws IOException {
        List<String> maps=availableStartingMaps();
        File selection=new File(live,"settings/default-map.json");
        if(selection.isFile()) {
            String value=AssetManifest.json(selection).trim();
            for(String map:maps)if(value.equals("\"maps/"+map+".skate\""))return map;
        }
        return maps.contains("University")?"University":maps.get(0);
    }
    void selectStartingMap(String map)throws IOException {
        try(LockHandle ignored=lock()) {
            recoverLocked();
            if(map==null||!availableStartingMaps().contains(map))throw new IOException("Starting map is not installed");
            File selection=new File(live,"settings/default-map.json");
            String original=selection.isFile()?AssetManifest.json(selection):null;
            String next="\"maps/"+map+".skate\"";
            if(next.equals(original))return;
            try {
                AssetManifest.writeAtomically(selection,next);
                AssetManifest.refreshSettings(live);
            } catch(IOException failure) {
                if(original!=null) {
                    try {AssetManifest.writeAtomically(selection,original);AssetManifest.refreshSettings(live);}
                    catch(IOException restoreFailure){failure.addSuppressed(restoreFailure);}
                }
                throw failure;
            }
        }
    }
    private boolean playable(File directory) {
        try{AssetManifest manifest=AssetManifest.read(directory);manifest.verifyRuntime(directory);return true;}catch(IOException e){return false;}
    }
    void publish(File stage,ImportModel.Progress progress,ImportModel.Cancellation cancellation)throws IOException {
        try(LockHandle ignored=lock()) {
            recoverLocked();
            File pending=stage.getCanonicalFile();
            if(!pending.getParentFile().equals(root)||!pending.getName().matches("import-[A-Za-z0-9_-]{1,80}\\.pending")||Files.isSymbolicLink(stage.toPath()))throw new IOException("Not a job-owned staging directory");
            AssetManifest manifest=AssetManifest.read(pending);manifest.verify(pending,progress,cancellation);cancellation.check();
            if(live.isDirectory())preserveSettings(pending,manifest);
            // No cancellation after journal publication: startup will finish or roll back.
            progress.report(ImportModel.Phase.PUBLISHING,0,-1,"");
            journal("validated",pending.getName());faults.after("validated");
            if(live.exists()) {
                if(previous.exists())deleteOwned(previous);
                move(live,previous);
            }
            journal("old-renamed",pending.getName());faults.after("old-renamed");
            move(pending,live);journal("new-renamed",pending.getName());faults.after("new-renamed");
            journal("committed",pending.getName());faults.after("committed");
            Files.deleteIfExists(journal.toPath());
        }
    }
    void restorePrevious()throws IOException {
        try(LockHandle ignored=lock()) {
            recoverLocked();if(!playable(previous))throw new IOException("No valid previous installation");
            File temporary=new File(root,"import-restore.pending");if(temporary.exists())throw new IOException("Recovery directory already exists");
            move(previous,temporary);
            // Route restoration through the same journal. Live remains recoverable until staged publication.
            journal("validated",temporary.getName());
            if(live.exists())move(live,previous);
            journal("old-renamed",temporary.getName());move(temporary,live);
            journal("committed",temporary.getName());Files.deleteIfExists(journal.toPath());
        }
    }
    private void recoverLocked()throws IOException {
        if(!journal.isFile()) {
            if(!live.exists()&&playable(previous))move(previous,live);
            return;
        }
        Properties state=new Properties();try(InputStream in=new FileInputStream(journal)){state.load(in);}
        String name=state.getProperty("pending","");
        if(!name.matches("import-[A-Za-z0-9_-]{1,80}\\.pending"))throw new IOException("Invalid publication journal");
        File pending=new File(root,name);
        if(!live.exists()) {
            if(playable(pending))move(pending,live);
            else if(playable(previous))move(previous,live);
            else throw new IOException("Interrupted import has no recoverable installation");
        } else if(!playable(live)) {
            if(!playable(previous))throw new IOException("Cannot recover previous installation");
            File failed=new File(root,"import-failed-"+UUID.randomUUID()+".pending");move(live,failed);move(previous,live);
        }
        Files.deleteIfExists(journal.toPath());
    }
    private void preserveSettings(File pending,AssetManifest manifest)throws IOException {
        File settings=new File(live,"settings");if(!settings.isDirectory())return;
        try(var paths=Files.walk(settings.toPath())) {
            for(Iterator<Path> it=paths.iterator();it.hasNext();) {
                Path from=it.next();if(Files.isSymbolicLink(from))throw new IOException("Settings links are unsupported");
                if(Files.isRegularFile(from)) {
                    String relative="settings/"+settings.toPath().relativize(from).toString().replace(File.separatorChar,'/');
                    File to=AssetManifest.contained(pending,relative);Files.createDirectories(to.toPath().getParent());Files.copy(from,to.toPath(),StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        File selection=new File(pending,"settings/default-map.json");
        if(selection.isFile()) {
            String stored=AssetManifest.json(selection).trim();
            if(!stored.equals("null")&&!manifest.maps.stream().anyMatch(map->stored.equals("\"maps/"+map+".skate\""))) {
                String fallback=manifest.maps.contains("University")?"University":manifest.maps.get(0);
                writeSync(selection,"\"maps/"+fallback+".skate\"");
            }
        }
        AssetManifest.refreshSettings(pending);
    }
    private void journal(String phase,String pending)throws IOException {
        File next=new File(root,"import-publication.properties.tmp");
        writeSync(next,"phase="+phase+"\npending="+pending+"\n");
        Files.move(next.toPath(),journal.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private static void writeSync(File file,String text)throws IOException {
        try(FileOutputStream out=new FileOutputStream(file)){out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}
    }
    private static void move(File from,File to)throws IOException {Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);}
    private void deleteOwned(File file)throws IOException {
        if(!file.getCanonicalFile().getParentFile().equals(root)||!file.equals(previous))throw new IOException("Refusing to remove unowned directory");
        try(var paths=Files.walk(file.toPath())){for(Path p:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(p);}
    }
    private LockHandle lock()throws IOException {
        FileChannel channel=FileChannel.open(new File(root,"import.lock").toPath(),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try {FileLock lock=channel.tryLock();if(lock==null)throw new IOException("Another import is active");return new LockHandle(channel,lock);}
        catch(IOException|OverlappingFileLockException e){channel.close();throw new IOException("Another import is active",e);}
    }
    private static final class LockHandle implements AutoCloseable {
        final FileChannel channel;final FileLock lock;
        LockHandle(FileChannel channel,FileLock lock){this.channel=channel;this.lock=lock;}
        public void close()throws IOException{try{lock.release();}finally{channel.close();}}
    }
}
