package com.skate3rust.android;
final class StartupStatus {
    static {System.loadLibrary("skate3rust_android");}
    static native int stage();
    static native boolean takeLayoutRequest();
}
