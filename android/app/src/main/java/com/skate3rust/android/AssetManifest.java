package com.skate3rust.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.json.*;

/** Strict, bounded prepared-asset contract. Hashes detect corruption, not authorship. */
final class AssetManifest {
    static final int MAX_ENTRIES=100000, MAX_JSON=32*1024*1024, MAX_PATH=512;
    static final long MAX_BYTES=32L*1024*1024*1024;
    static final class Entry {
        final String path,sha256; final long size;
        Entry(String path,long size,String sha256){this.path=path;this.size=size;this.sha256=sha256;}
    }
    final Map<String,Entry> entries;
    final List<String> maps;
    final long totalBytes;
    final int version=1;
    private AssetManifest(Map<String,Entry> entries,List<String> maps,long total){
        this.entries=Collections.unmodifiableMap(entries);this.maps=Collections.unmodifiableList(maps);totalBytes=total;
    }
    static AssetManifest read(File root)throws IOException {
        try {
            JSONObject complete=new JSONObject(json(new File(root,"complete.json")));
            if(complete.getInt("version")!=1)throw new IOException("Unsupported prepared asset version");
            JSONArray raw=new JSONArray(json(new File(root,"transfer-manifest.json")));
            if(raw.length()==0||raw.length()>MAX_ENTRIES)throw new IOException("Invalid asset file count");
            Map<String,Entry> entries=new LinkedHashMap<>();Set<String> folded=new HashSet<>();long total=0;
            for(int i=0;i<raw.length();i++) {
                JSONObject record=raw.getJSONObject(i);String path=record.getString("path");
                validatePath(path);
                if(!(path.startsWith("assets/")||path.startsWith("maps/")||path.startsWith("settings/")))throw new IOException("Unexpected asset root");
                long size=record.getLong("size");String sha=record.getString("sha256");
                if(size<0||size>MAX_BYTES||!sha.matches("[0-9a-fA-F]{64}"))throw new IOException("Invalid asset size/hash");
                if(!folded.add(path.toLowerCase(Locale.ROOT)))throw new IOException("Duplicate asset path");
                total=Math.addExact(total,size);if(total>MAX_BYTES)throw new IOException("Asset package is too large");
                entries.put(path,new Entry(path,size,sha.toLowerCase(Locale.ROOT)));
            }
            if(complete.getInt("files")!=entries.size()||complete.getLong("bytes")!=total)throw new IOException("Asset totals do not match manifest");
            List<String> maps=new ArrayList<>();JSONArray names=complete.getJSONArray("maps");
            for(int i=0;i<names.length();i++) {
                String name=names.getString(i);
                if(!name.matches("[A-Za-z0-9_-]{1,100}")||maps.contains(name)||!entries.containsKey("maps/"+name+".skate"))throw new IOException("Invalid installed map catalog");
                maps.add(name);
            }
            if(maps.isEmpty())throw new IOException("No maps in prepared assets");
            return new AssetManifest(entries,maps,total);
        } catch(JSONException|ArithmeticException e){throw new IOException("Invalid prepared asset metadata",e);}
    }
    void verify(File root,ImportModel.Progress progress,ImportModel.Cancellation cancel)throws IOException {
        long done=0;byte[] buffer=new byte[128*1024];
        for(Entry e:entries.values()) {
            cancel.check();File file=contained(root,e.path);
            if(!file.isFile()||file.length()!=e.size)throw new IOException("Missing or truncated asset: "+e.path);
            MessageDigest hash=digest();long count=0;
            try(InputStream in=new FileInputStream(file)) {
                int n;while((n=in.read(buffer))!=-1){cancel.check();count+=n;if(count>e.size)throw new IOException("Asset changed during validation");hash.update(buffer,0,n);}
            }
            if(count!=e.size||!hex(hash.digest()).equals(e.sha256))throw new IOException("Asset checksum mismatch: "+e.path);
            done+=count;progress.report(ImportModel.Phase.VALIDATING,done,totalBytes,e.path);
        }
        try(var stream=Files.walk(root.toPath())) {
            for(Iterator<Path> it=stream.iterator();it.hasNext();) {
                cancel.check();Path p=it.next();
                if(Files.isSymbolicLink(p))throw new IOException("Asset links are not supported");
                if(Files.isRegularFile(p)) {
                    String relative=root.toPath().relativize(p).toString().replace(File.separatorChar,'/');
                    if(!entries.containsKey(relative)&&!relative.equals("complete.json")&&!relative.equals("transfer-manifest.json")&&!relative.equals("sha256sum.checks"))throw new IOException("Unlisted asset: "+relative);
                }
            }
        }
        verifyRuntime(root);
    }
    /** Structural preflight only; full native data validation is a separate stage. */
    void verifyRuntime(File root)throws IOException {
        try {
            JSONObject game=new JSONObject(json(contained(root,"assets/private/game.json")));
            if(game.getInt("version")!=1||game.getString("initial_animation").trim().isEmpty())throw new IOException("Unsupported skater manifest");
            for(String key:new String[]{"character_scene","action_graph","motion_graph"}) {
                String path=game.getString(key);validatePath(path);
                if(!entries.containsKey("assets/"+path)||!contained(root,"assets/"+path).isFile())throw new IOException("Missing runtime resource: "+key);
            }
            for(String map:maps)if(!contained(root,"maps/"+map+".skate").isFile())throw new IOException("Missing map");
        } catch(JSONException e){throw new IOException("Invalid skater manifest",e);}
    }
    /** Refresh imported-file integrity metadata after carrying forward app settings. */
    static void refreshSettings(File root)throws IOException {
        try {
            File manifestFile=new File(root,"transfer-manifest.json");
            JSONArray current=new JSONArray(json(manifestFile));
            TreeMap<String,Entry> records=new TreeMap<>();
            for(int i=0;i<current.length();i++) {
                JSONObject record=current.getJSONObject(i);String path=record.getString("path");
                records.put(path,new Entry(path,record.getLong("size"),record.getString("sha256")));
            }
            File settings=new File(root,"settings");
            if(settings.isDirectory())try(var paths=Files.walk(settings.toPath())) {
                for(Iterator<Path> it=paths.iterator();it.hasNext();) {
                    Path file=it.next();if(Files.isSymbolicLink(file))throw new IOException("Settings links are unsupported");
                    if(!Files.isRegularFile(file))continue;
                    String path="settings/"+settings.toPath().relativize(file).toString().replace(File.separatorChar,'/');
                    validatePath(path);long size=Files.size(file);
                    if(size>MAX_BYTES)throw new IOException("Settings file is too large");
                    MessageDigest hash=digest();try(InputStream in=Files.newInputStream(file)) {
                        byte[] buffer=new byte[64*1024];int n;while((n=in.read(buffer))!=-1)hash.update(buffer,0,n);
                    }
                    records.put(path,new Entry(path,size,hex(hash.digest())));
                }
            }
            long total=0;JSONArray refreshed=new JSONArray();StringBuilder checks=new StringBuilder();
            for(Entry entry:records.values()) {
                total=Math.addExact(total,entry.size);if(total>MAX_BYTES||records.size()>MAX_ENTRIES)throw new IOException("Updated asset package is too large");
                refreshed.put(new JSONObject().put("path",entry.path).put("size",entry.size).put("sha256",entry.sha256));
                checks.append(entry.sha256).append("  ").append(entry.path).append('\n');
            }
            JSONObject complete=new JSONObject(json(new File(root,"complete.json")));
            complete.put("files",records.size()).put("bytes",total);
            writeAtomically(manifestFile,refreshed.toString());
            writeAtomically(new File(root,"complete.json"),complete.toString());
            writeAtomically(new File(root,"sha256sum.checks"),checks.toString());
        } catch(JSONException|ArithmeticException e){throw new IOException("Could not refresh settings integrity metadata",e);}
    }
    private static void writeAtomically(File target,String content)throws IOException {
        File temporary=new File(target.getParentFile(),target.getName()+".tmp");
        try(FileOutputStream output=new FileOutputStream(temporary)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));output.getFD().sync();
        }
        Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    static void validatePath(String path)throws IOException {
        if(path.isEmpty()||path.length()>MAX_PATH||path.contains("\\")||path.contains(":")||path.startsWith("/")||path.chars().anyMatch(c->c<32||c==127))throw new IOException("Invalid asset path");
        for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals("..")||part.endsWith(".")||part.endsWith(" "))throw new IOException("Invalid asset path segment");
    }
    static File contained(File root,String path)throws IOException {
        validatePath(path);File canonical=root.getCanonicalFile();File result=new File(root,path).getCanonicalFile();
        if(!result.toPath().startsWith(canonical.toPath())||result.equals(canonical))throw new IOException("Asset path escapes installation");
        Path cursor=root.toPath();for(String segment:path.split("/")){cursor=cursor.resolve(segment);if(Files.isSymbolicLink(cursor))throw new IOException("Asset links are not supported");}
        return result;
    }
    static String json(File file)throws IOException {
        if(!file.isFile()||file.length()>MAX_JSON)throw new IOException("Missing or oversized metadata: "+file.getName());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];
        try(InputStream in=new FileInputStream(file)){int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>MAX_JSON)throw new IOException("Oversized metadata");bytes.write(buffer,0,n);}}
        String text=new String(bytes.toByteArray(),StandardCharsets.UTF_8);return text.startsWith("\ufeff")?text.substring(1):text;
    }
    static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
}