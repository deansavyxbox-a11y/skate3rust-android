package com.skate3rust.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImportProgressTextTest {
    @Test public void conversionWithoutByteEstimateShowsMapAndStepInsteadOfZeroMegabytes() {
        String text=ImportProgressText.render(ImportModel.Phase.CONVERTING,0,-1,
            "Writing DownTown: model 301/649","Converting map 2 of 10: DownTown");
        assertTrue(text.contains("Converting map 2 of 10: DownTown"));
        assertTrue(text.contains("Writing DownTown: model 301/649"));
        assertFalse(text.contains("0.0 MB"));
    }

    @Test public void byteBasedCopyStillShowsMeasuredProgress() {
        String text=ImportProgressText.render(ImportModel.Phase.CONVERTING,4_000_000,8_000_000,
            "Copying the selected ISO into private storage","");
        assertTrue(text.contains("4.0 MB / 8.0 MB"));
    }

    @Test public void mapContextSurvivesInternalStepsAndEndsBeforeParkPreparation() {
        String map=ImportProgressText.mapContext("","Converting map 2 of 10: DownTown");
        assertEquals("Converting map 2 of 10: DownTown",ImportProgressText.mapContext(map,"Writing DownTown: model 301/649"));
        assertEquals("",ImportProgressText.mapContext(map,"Preparing full skate park layout: MegaPark"));
    }

    @Test public void unknownValidationBytesDoNotDisplayZeroMegabytes() {
        String text=ImportProgressText.render(ImportModel.Phase.VALIDATING,0,-1,
            "Checking converted game assets","");
        assertFalse(text.contains("0.0 MB"));
        assertTrue(text.contains("Checking converted game assets"));
    }

    @Test public void stepChangesRefreshTheVisibleProgress() {
        assertTrue(ImportProgressText.shouldRefresh("item"));
        assertTrue(ImportProgressText.shouldRefresh("map_progress"));
        assertFalse(ImportProgressText.shouldRefresh("uri"));
    }
}
