package com.skate3rust.android;

/** Calls renderer-free Rust parsers from the import service, never the game library. */
final class NativeAssetValidator {
    static { System.loadLibrary("skate3rust_import"); }
    interface Progress { boolean isCancelled(); void onProgress(long completed,long total,String item); }
    static native String nativeValidate(String installationRoot);
    static native String nativeExtractIso(String sourceIso,String destination,Progress progress)throws java.io.IOException;
    static native String nativeExtractIsoFd(int sourceFd,String destination,Progress progress)throws java.io.IOException;
    private NativeAssetValidator() {}
}
