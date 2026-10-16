package com.skate3rust.android;
/** Bounds disk and notification work independently of chunk/file count. */
final class ImportProgressThrottle {
 private ImportModel.Phase lastPhase;
 private long lastTime;
 boolean shouldEmit(ImportModel.Phase phase,long now) {
  if(phase==lastPhase && now-lastTime<500)return false;
  lastPhase=phase;lastTime=now;return true;
 }
}
