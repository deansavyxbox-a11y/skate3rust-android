package com.skate3rust.android;

/** Calls renderer-free Rust parsers from the import service, never the game library. */
final class NativeAssetValidator {
    static { System.loadLibrary("skate3rust_import"); }
    static native String nativeValidate(String installationRoot);
    private NativeAssetValidator() {}
}
