package com.skate3rust.android;

import org.json.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;

public class NativeManifestTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void createsZipCompatibleManifestFromNativeConversionOutput()throws Exception {
        File root=ImportFixtures.create(temp.newFolder(),"University");
        ImportFixtures.write(root,"maps.json",new JSONArray().put(new JSONObject().put("name","University").put("path","maps/University.skate")).toString());
        AssetManifest manifest=AssetManifest.create(root,(phase,done,total,item)->{},()->{});
        assertEquals(1,manifest.maps.size());assertEquals("University",manifest.maps.get(0));
        assertFalse(new File(root,"maps.json").exists());
        manifest.verify(root,(phase,done,total,item)->{},()->{});
    }
}
