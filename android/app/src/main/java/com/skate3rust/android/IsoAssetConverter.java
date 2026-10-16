package com.skate3rust.android;

import android.content.Context;
import android.content.res.AssetManager;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.*;

/** Runs the existing owned-disc converters in the embedded ARM64 Python runtime. */
public final class IsoAssetConverter {
    public interface Callbacks {
        void report(String message);
        void checkCancelled() throws IOException;
    }

    static void convert(Context context, File gameRoot, File stage, File work, Callbacks callbacks) throws IOException {
        File source = new File(context.getCacheDir(), "skate3rust-python-source");
        boolean started = Python.isStarted();
        if (!started) {
            deleteTree(source);
            copyAssets(context.getAssets(), "android-python", source);
            Python.start(new AndroidPlatform(context));
        }
        try {
            Python python = Python.getInstance();
            python.getModule("sys").get("path").callAttr("insert", 0, source.getAbsolutePath());
            python.getModule("os").get("environ").callAttr("__setitem__", "SKATE_REFPACK_LIBRARY",
                    new File(context.getApplicationInfo().nativeLibraryDir,"libskate3rust_import.so").getAbsolutePath());
            PyObject module = python.getModule("android_entry");
            module.callAttr("convert_owned", gameRoot.getAbsolutePath(), stage.getAbsolutePath(),
                    work.getAbsolutePath(), callbacks, source.getAbsolutePath());
        } catch (RuntimeException error) {
            Throwable cause = error.getCause();
            String message = cause == null ? error.getMessage() : cause.getMessage();
            throw new IOException(message == null ? "On-device asset conversion failed." : message, error);
        }
    }

    private static void copyAssets(AssetManager assets, String relative, File destination) throws IOException {
        String[] children = assets.list(relative);
        if (children == null) throw new IOException("The embedded converter source is missing.");
        if (children.length > 0) {
            if (!destination.isDirectory() && !destination.mkdirs()) throw new IOException("Could not prepare converter runtime.");
            for (String child : children) copyAssets(assets, relative + "/" + child, new File(destination, child));
        } else {
            File parent = destination.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not prepare converter runtime.");
            try (InputStream input = assets.open(relative); OutputStream output = new FileOutputStream(destination)) {
                byte[] buffer=new byte[32*1024];int count;
                while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
            }
        }
    }

    private static void deleteTree(File root) {
        if (!root.exists()) return;
        File[] children = root.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        root.delete();
    }
    private IsoAssetConverter() {}
}
