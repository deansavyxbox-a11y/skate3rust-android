# Skate3Rust Android 0.2.0-rc1

Unpublished release candidate. Package `com.skate3rust.android`, version code 2.

## Changes from 0.1.0-experimental

- Choose prepared asset ZIP import or direct conversion of your own Xbox 360 ISO on the phone.
- Validated publication retains a previous asset generation for restoration and preserves existing settings.
- Direct conversion includes ten district maps and the full supplemental layouts for DownTownSkatePark, IndustrialSkatePark, MegaPark and StartPark.
- Import progress updates, interrupted-job retry, native RefPack decompression and ZIP end-of-archive validation are corrected.
- Retry reclaims the interrupted job's private staging directory after recovering any publication journal, preserving installed assets and unrelated files.
- The loading screen releases focus before native startup to avoid the reproduced startup focus-event ANR.
- Installation and error-code troubleshooting instructions now distinguish APK installation from asset/rendering issues.

## Install and update

Use the candidate APK and checksum from the same artifact set. Existing users should install over their current copy; do not uninstall or clear app data. Version code increases from 1 to 2, with the existing signing certificate retained. The APK remains debuggable to retain compatibility with the PC `run-as` asset installer. Contributor builds using another key cannot necessarily update this copy.

ARM64 Android userspace and Android 8 / API 26 or newer are installation requirements. Compatible Vulkan graphics are required for gameplay. SDK, ABI and Vulkan requirements are unchanged. Only RedMagic 10 Pro on Android 16 has been tested; other devices, older Android versions and 16 KB runtime operation remain unverified. This is not a universal compatibility release. The viewer's original “App not installed” cause remains unknown without a device model and ADB failure code.

## Assets and known limits

No proprietary Skate 3 assets are included. Supply your own Xbox 360 disc image or a privately prepared Android asset package. A GitHub source ZIP is not an asset package. Direct ISO conversion requires substantial free internal storage; the full test began with about 38 GB free after the ISO was already on the phone. Keep the app open and the phone powered. Conversion may take time, cancellation may wait for a converter stage, and Retry restarts conversion after interruption.

Native gameplay initialization may take tens of seconds. Existing material/decal, dynamic-object, audio, wardrobe and multiplayer limitations remain. All-map trick/respawn regression and sustained thermal behavior are not established.

The embedded FreeType dependency has 4 KB ELF alignment, so this candidate is not cleared for 16 KB devices. The final startup check also observed a close/clipped camera view at the saved DownTownSkatePark spawn; this needs focused follow-up. Interrupted-job cleanup has unit coverage but still needs a final on-device kill/retry check. See the candidate report for remaining publication gates.

See [setup](../docs/ANDROID_PORT.md), [candidate validation](RELEASE_CANDIDATE_0.2.0_RC1.md), [ISO validation](ISO_IMPORT_VALIDATION.md), and [the exact previous public APK audit](../docs/APK_COMPATIBILITY_AUDIT.md).
