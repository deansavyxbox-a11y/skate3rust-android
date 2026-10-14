package com.skate3rust.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import org.json.*;

public class InstallationStoreTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final ImportModel.Progress progress=(phase,done,total,item)->{};
    private final ImportModel.Cancellation cancel=()->{};
    @Test public void publishesValidatedStaging()throws Exception {
        File root=temp.newFolder(); InstallationStore store=new InstallationStore(root);
        File staging=ImportFixtures.create(store.newStaging("first"),"University");
        store.publish(staging,progress,cancel);
        assertTrue(store.isPlayable()); assertFalse(staging.exists());
    }
    @Test public void rejectsBadManifestAndMissingRuntimeReferences()throws Exception {
        File stage=ImportFixtures.create(temp.newFolder(),"University");
        ImportFixtures.write(stage,"maps/University.skate","corrupted");
        assertThrows(IOException.class,()->AssetManifest.read(stage).verify(stage,progress,cancel));
        ImportFixtures.manifest(stage,"University");
        Files.delete(stage.toPath().resolve("assets/private/action.stategraph"));
        ImportFixtures.manifest(stage,"University");
        assertThrows(IOException.class,()->AssetManifest.read(stage).verify(stage,progress,cancel));
    }
    @Test public void rejectsTraversalDuplicatesAndInconsistentTotals()throws Exception {
        File stage=ImportFixtures.create(temp.newFolder(),"University");
        String original=ImportFixtures.read(stage,"transfer-manifest.json");
        JSONArray records=new JSONArray(original); records.getJSONObject(0).put("path","../escape");
        ImportFixtures.write(stage,"transfer-manifest.json",records.toString());
        assertThrows(IOException.class,()->AssetManifest.read(stage));
        records=new JSONArray(original);records.put(records.getJSONObject(0));
        ImportFixtures.write(stage,"transfer-manifest.json",records.toString());
        assertThrows(IOException.class,()->AssetManifest.read(stage));
    }
    @Test public void preservesGraphicsTouchAndFallsBackForMissingMap()throws Exception {
        File root=temp.newFolder();InstallationStore store=new InstallationStore(root);
        File old=ImportFixtures.create(new File(root,"installation"),"MegaPark");
        ImportFixtures.write(old,"settings/graphics.json","{\"fps\":0,\"scale\":50}");
        ImportFixtures.write(root,"../touch-layout-sentinel.txt","unchanged");
        File next=ImportFixtures.create(store.newStaging("next"),"University");
        store.publish(next,progress,cancel);
        assertEquals("{\"fps\":0,\"scale\":50}",ImportFixtures.read(new File(root,"installation"),"settings/graphics.json"));
        assertEquals("\"maps/University.skate\"",ImportFixtures.read(new File(root,"installation"),"settings/default-map.json"));
        assertEquals("unchanged",ImportFixtures.read(root,"../touch-layout-sentinel.txt"));
        AssetManifest.read(new File(root,"installation")).verify(new File(root,"installation"),progress,cancel);
        store.restorePrevious(); assertTrue(new File(root,"installation/maps/MegaPark.skate").isFile());
    }
    @Test public void recoversEveryPublicationBoundary()throws Exception {
        for(String point:new String[]{"validated","old-renamed","new-renamed","committed"}) {
            File root=temp.newFolder();ImportFixtures.create(new File(root,"installation"),"MegaPark");
            InstallationStore store=new InstallationStore(root,stage->{if(stage.equals(point))throw new IOException("simulated process death");});
            File next=ImportFixtures.create(store.newStaging("next"),"University");
            assertThrows(IOException.class,()->store.publish(next,progress,cancel));
            InstallationStore recovered=new InstallationStore(root);recovered.recover();
            assertTrue("must retain playable assets after "+point,recovered.isPlayable());
            File live=new File(root,"installation");
            assertTrue(new File(live,"maps/MegaPark.skate").isFile()||new File(live,"maps/University.skate").isFile());
        }
    }
    @Test public void cancellationAndInvalidStagingLeaveOldInstall()throws Exception {
        File root=temp.newFolder(); ImportFixtures.create(new File(root,"installation"),"MegaPark");
        InstallationStore store=new InstallationStore(root);File next=ImportFixtures.create(store.newStaging("next"),"University");
        assertThrows(InterruptedIOException.class,()->store.publish(next,progress,()->{throw new InterruptedIOException("cancel");}));
        assertTrue(new File(root,"installation/maps/MegaPark.skate").isFile());
        File outside=ImportFixtures.create(temp.newFolder(),"University");
        assertThrows(IOException.class,()->store.publish(outside,progress,cancel));
    }
    @Test public void acceptsPowerShellBomAndRejectsUnlistedPayload()throws Exception {
        File stage=ImportFixtures.create(temp.newFolder(),"University");
        ImportFixtures.write(stage,"complete.json","\ufeff"+ImportFixtures.read(stage,"complete.json"));
        AssetManifest.read(stage).verify(stage,progress,cancel);
        ImportFixtures.write(stage,"assets/unlisted","extra");
        assertThrows(IOException.class,()->AssetManifest.read(stage).verify(stage,progress,cancel));
    }
}