package com.skate3rust.android;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import org.json.*;

final class ImportFixtures {
    static File create(File root, String map) throws Exception {
        root.mkdirs();
        write(root,"assets/private/game.json",new JSONObject().put("version",1)
            .put("character_scene","private/skater.glb").put("initial_animation","idle")
            .put("action_graph","private/action.stategraph").put("motion_graph","private/motion.stategraph").toString());
        write(root,"assets/private/skater.glb","fixture character");
        write(root,"assets/private/action.stategraph","fixture action");
        write(root,"assets/private/motion.stategraph","fixture motion");
        write(root,"maps/"+map+".skate","fixture map");
        write(root,"settings/default-map.json","\"maps/"+map+".skate\"");
        write(root,"settings/graphics.json","{\"fps\":30}");
        manifest(root,map);
        return root;
    }
    static void manifest(File root,String map) throws Exception {
        manifest(root,new String[]{map});
    }
    static void manifest(File root,String... maps) throws Exception {
        JSONArray entries=new JSONArray(); long total=0;
        try(var paths=Files.walk(root.toPath())) {
            for(Path file:paths.filter(Files::isRegularFile).sorted().toList()) {
                String name=root.toPath().relativize(file).toString().replace('\\','/');
                if(name.equals("complete.json")||name.equals("transfer-manifest.json"))continue;
                byte[] bytes=Files.readAllBytes(file); total+=bytes.length;
                entries.put(new JSONObject().put("path",name).put("size",bytes.length).put("sha256",sha(bytes)));
            }
        }
        write(root,"transfer-manifest.json",entries.toString());
        write(root,"complete.json",new JSONObject().put("version",1).put("files",entries.length()).put("bytes",total)
            .put("map",maps[0]).put("maps",new JSONArray(maps)).toString());
    }
    static String sha(byte[] bytes)throws Exception{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static void write(File root,String name,String text)throws IOException {
        Path path=root.toPath().resolve(name); Files.createDirectories(path.getParent()); Files.write(path,text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    static String read(File root,String name)throws IOException{return new String(Files.readAllBytes(root.toPath().resolve(name)),java.nio.charset.StandardCharsets.UTF_8);}
}
