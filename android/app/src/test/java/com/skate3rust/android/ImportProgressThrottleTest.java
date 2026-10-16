package com.skate3rust.android;
import org.junit.Test;
import static org.junit.Assert.*;
public class ImportProgressThrottleTest {
 @Test public void boundsChunkUpdatesButImmediatelyReportsPhaseChanges() {
  ImportProgressThrottle gate=new ImportProgressThrottle();
  assertTrue(gate.shouldEmit(ImportModel.Phase.IMPORTING,0));
  for(int i=1;i<500;i++) assertFalse(gate.shouldEmit(ImportModel.Phase.IMPORTING,i));
  assertTrue(gate.shouldEmit(ImportModel.Phase.IMPORTING,500));
  assertTrue(gate.shouldEmit(ImportModel.Phase.VALIDATING,501));
  assertFalse(gate.shouldEmit(ImportModel.Phase.VALIDATING,502));
 }
}
