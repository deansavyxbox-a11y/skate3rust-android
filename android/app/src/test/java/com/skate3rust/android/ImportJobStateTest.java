package com.skate3rust.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImportJobStateTest {
    @Test public void onlyOneJobRuns() {
        ImportJobState state=new ImportJobState();
        assertTrue(state.start("one",ImportModel.Phase.INSPECTING));
        assertFalse(state.start("two",ImportModel.Phase.INSPECTING));
        state.finish(ImportModel.Phase.COMPLETE,"done");
        assertTrue(state.start("two",ImportModel.Phase.INSPECTING));
    }
    @Test public void terminalJobRestoresAsInterrupted() {
        ImportJobState state=new ImportJobState();
        state.start("one",ImportModel.Phase.IMPORTING);
        ImportJobState.Snapshot restored=ImportJobState.restore("one",ImportModel.Phase.IMPORTING,14,100,"map");
        assertEquals(ImportModel.Phase.INTERRUPTED,restored.phase);
        assertEquals(14,restored.completed);
        assertEquals("map",restored.item);
    }
    @Test public void rejectsInvalidProgressAndNeverRegressesTerminalState() {
        ImportJobState state=new ImportJobState();state.start("one",ImportModel.Phase.IMPORTING);
        state.report(ImportModel.Phase.IMPORTING,-1,100,"entry");
        assertEquals(0,state.snapshot().completed);
        state.finish(ImportModel.Phase.FAILED,"bad archive");
        state.report(ImportModel.Phase.IMPORTING,90,100,"entry");
        assertEquals(ImportModel.Phase.FAILED,state.snapshot().phase);
    }
    @Test public void interruptedWorkerDoesNotKeepSetupLockedAfterProcessRestart() {
        assertEquals(ImportModel.Phase.INTERRUPTED,ImportJobState.visiblePhase(ImportModel.Phase.CONVERTING,false));
        assertEquals(ImportModel.Phase.CONVERTING,ImportJobState.visiblePhase(ImportModel.Phase.CONVERTING,true));
        assertEquals(ImportModel.Phase.COMPLETE,ImportJobState.visiblePhase(ImportModel.Phase.COMPLETE,false));
        assertEquals(ImportModel.Phase.IDLE,ImportJobState.visiblePhase(ImportModel.Phase.IDLE,false));
    }
}
