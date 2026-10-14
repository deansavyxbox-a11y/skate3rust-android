package com.skate3rust.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class SetupRouteTest {
    @Test public void activeNativeGameAlwaysReturnsToGame() {
        assertEquals(SetupRoute.Destination.GAME,SetupRoute.destination(true));
    }
    @Test public void inactiveNativeGameShowsSetup() {
        assertEquals(SetupRoute.Destination.SETUP,SetupRoute.destination(false));
    }
}
