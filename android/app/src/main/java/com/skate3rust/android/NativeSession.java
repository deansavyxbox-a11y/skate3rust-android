package com.skate3rust.android;

/** Process-local guard: installation files cannot be replaced after Bevy has started. */
final class NativeSession {
    private static volatile boolean active;
    static boolean isActive(){return active;}
    static void begin(){active=true;}
    static void end(){active=false;}
    private NativeSession(){}
}
