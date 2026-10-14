package com.skate3rust.android;

/** Keeps installation controls out of a live native gameplay process. */
final class SetupRoute {
    enum Destination { SETUP, GAME }
    static Destination destination(boolean nativeSessionActive) {
        return nativeSessionActive?Destination.GAME:Destination.SETUP;
    }
    private SetupRoute() {}
}
