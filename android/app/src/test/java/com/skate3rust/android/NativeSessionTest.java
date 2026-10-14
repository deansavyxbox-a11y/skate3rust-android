package com.skate3rust.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeSessionTest {
    @Test public void setupIsBlockedOnlyWhileGameplaySessionIsAlive() {
        NativeSession.end();assertFalse(NativeSession.isActive());
        NativeSession.begin();assertTrue(NativeSession.isActive());
        NativeSession.end();assertFalse(NativeSession.isActive());
    }
}
