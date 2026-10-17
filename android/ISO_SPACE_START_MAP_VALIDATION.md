# ISO storage and starting-map validation — 2026-10-17

This report covers the development branch `feature/iso-space-start-map`. The published `0.2.0-rc4` APK does not include these changes.

## Changes

- The setup screen lists maps from the verified active installation. The selected map is saved before Play and remains selected across cold starts. A missing map is rejected; the settings integrity manifest is refreshed after a change.
- ISO import first asks Android's document provider for a seekable file descriptor. A seekable document goes directly to the existing native disc reader. A pipe or unsupported descriptor retains the streaming private-copy path. The fallback copy is removed immediately after extraction, before map conversion.
- Setup explains that direct reading saves one ISO-size temporary copy, while disc extraction, conversion intermediates and a new asset generation still require substantial storage.

## Validation

- Android unit suite: 33 tests, 0 failures. Native ARM64 library and debug APK assembled successfully. `git diff --check` passed.
- The APK updated the existing installation on RedMagic 10 Pro (NX789J, Android 16) using `adb install -r`; the existing app data remained present.
- The setup picker displayed all 10 installed maps. Selecting StartPark before Play wrote `settings/default-map.json`; a cold restart reopened with StartPark selected. StartPark launched and rendered its park, skater and touch controls after the new ISO import.
- The phone selected the user's existing 7,838,695,424-byte Xbox 360 ISO from Android DocumentsUI. Native extraction reported a 6,404,940,920-byte disc payload and the job reached `COMPLETE` after conversion and publication. No private `source.iso` appeared in staging during extraction or conversion. The source ISO remained in Downloads at its original size.
- Rounded `/data` free-space readings were about 43 GB before import, at least 35 GB during sampled conversion, and 42 GB after cleanup. These are approximate device-wide readings, not a precise peak workspace measurement. The avoided private copy is the source ISO's 7.84 GB; conversion still temporarily used several GB.
- The active installation is about 1.0 GB after import. The previous installation remains available for recovery. The pre-test DownTown starting-map choice was restored at the end.

## Remaining limits

The stream-copy fallback is retained and covered by the previously working code path, but a nonseekable document provider was not available for a new on-device test. Other ISO variants, devices and low-storage conditions remain unverified. StartPark loaded and rendered on the RedMagic; this does not establish that a Redmi Note 7 Pro can run the app or that its reported black/logo screen has the same cause. No proprietary assets are included in the repository or APK. This branch has not been published as a public release.
