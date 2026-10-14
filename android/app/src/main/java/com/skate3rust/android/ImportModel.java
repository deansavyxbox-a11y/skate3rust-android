package com.skate3rust.android;

import java.io.InterruptedIOException;

/** Import contracts are independent of Android Activity and native game lifetime. */
final class ImportModel {
    enum Phase { IDLE, INSPECTING, IMPORTING, CONVERTING, VALIDATING, PUBLISHING, COMPLETE, CANCELLED, FAILED, INTERRUPTED }
    interface Progress { void report(Phase phase,long completed,long total,String item); }
    interface Cancellation { void check() throws InterruptedIOException; }
    private ImportModel() {}
}
