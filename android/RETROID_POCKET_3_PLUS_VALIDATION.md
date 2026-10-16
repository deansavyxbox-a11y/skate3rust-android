# Retroid Pocket 3 Plus validation — 2026-10-16

This test used the **exact public** [`0.2.0-rc2` APK](https://github.com/deansavyxbox-a11y/skate3rust-android/releases/tag/android-v0.2.0-rc2), downloaded from GitHub, not a local rebuild. Its SHA-256 matched the published `SHA256SUMS.txt`: `54d3f9c5ca15ff6a03887eefe8adc7f287b402e9818208815b59f8df54bf7961`. The device reported Retroid Pocket 3 Plus, Android 11 / API 30, `arm64-v8a` userspace, 4096-byte pages and Vulkan hardware level 1. `/proc/meminfo` reported 3,584,876 kB total memory. These feature declarations do not prove all game rendering features or maps will work.

Before the test, `pm path com.skate3rust.android` found no installed package. ADB fresh installation of the public APK returned `Performing Streamed Install` and **`Success`**, with no `INSTALL_FAILED_*` code. The launcher opened and both prepared-ZIP and direct-ISO picker buttons opened Android DocumentsUI. This confirms fresh install on this device; it does not explain the viewer reports from other devices or prove a same-device update path.

After import and the StartPark check, `adb install -r` of the same public APK also returned **`Success`**. The SHA-256 hashes of `settings/default-map.json` and `complete.json` matched before and after, and SetupActivity reopened with `Status: ok`. This is a same-version, same-signer reinstall that preserved the selected map and installation marker. It is **not** a cross-version upgrade test; imported asset payload bytes were not rehashed.

A privately held, 951,210,388-byte prepared ZIP was copied to the Retroid's Download folder and selected in the on-phone importer. Import completed with `Assets ready`, listing all ten supported maps, and displayed `Import complete. Assets are ready to play.` The ZIP and imported game assets were **not** committed, uploaded or included in the APK. Direct ISO conversion was **not tested** on the Retroid. About 5 GB of internal storage remained after this ZIP import.

The first gameplay launch used the imported University default map. Native startup reached `SKATE_RENDER_READY` (`draws=161`, `triangles=1,645,617`), but before a visible gameplay frame Android logged:

```text
lowmemorykiller: Kill 'com.skate3rust.android' (6153), uid 10135, oom_adj 0 to free 2646944kB
```

The process then exited with signal 9. This is a confirmed **runtime memory-pressure kill on this device and map**, not an APK installation, asset import or manifest Vulkan failure. It does not establish that every 4 GB device fails. The log's freed-kB figure is Android's reclaim report, not a controlled peak-memory measurement.

For a smaller-map check, the imported `settings/default-map.json` was backed up and pointed to `maps/StartPark.skate`. A fresh launch loaded StartPark (`32,206` render triangles), reached `SKATE_RENDER_READY` and remained running. A screenshot showed the skater and park rendered with an on-screen reading of **9 FPS**; one reading is not a benchmark. `dumpsys meminfo` at that point reported total PSS of 413,463 kB. Interactive skating, controls, map switching, update installation, 16 KB pages and sustained performance were not verified on this device. At the user's request, **StartPark remains the active default on the Retroid**. The original University map pointer was preserved as a device-local backup.

Next release work should measure and reduce University startup peak memory, then retest large maps on this device. Keep APK installation requirements separate from gameplay memory requirements. Do not relax the ARM64, SDK or Vulkan declarations, or change signing, based on this result alone.
