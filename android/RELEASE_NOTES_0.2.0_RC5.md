# Skate3Rust Android 0.2.0-rc5

Experimental ARM64 prerelease, package `com.skate3rust.android`, version code 6. This update reduces temporary storage during on-phone Xbox 360 ISO import when Android's document provider exposes a seekable file. In that case, the native reader extracts directly from the selected document instead of first making a full private ISO copy. Streaming-only providers still use a private copy, which is deleted after extraction. Extraction, conversion and publication still require several GB of free internal storage.

The setup screen now lists installed starting maps before Play. Players can select StartPark before launching on a lower-memory device. The choice is saved with the installation settings and preserved during an asset update. This offers a smaller tested starting map; it does not establish compatibility with a reported Redmi Note 7 Pro black/logo-screen failure.

Install over rc4 to retain imported assets, settings and touch layouts. The package ID, development signing certificate, minSdk 26, targetSdk 35, ARM64 ABI and required Vulkan feature are unchanged. The version code rises from 5 to 6, so Android will not normally permit an in-place downgrade to rc4. This remains a debuggable development-signed APK. No proprietary game assets are included.

## APK validation

- APK: 78,126,076 bytes; SHA-256 `16706726479fefaf21b0bc269c0f11b58885f24509837eec04ad7dcc69362a1c`.
- Android unit tests: 33 passed, 0 failures. Native ARM64 build and APK assembly passed.
- ZIP CRC, APK signature and 16 KB ZIP alignment checks passed. Package metadata reports version code 6, minSdk 26, targetSdk 35, ARM64 native libraries and required Vulkan level. The signing certificate SHA-256 is `82f0ecd863190a2e9224ad2be18455552a7c7804395d6949a80d4b701c7a4a81`, matching rc4. No `.iso`, `.xex`, `.xbe`, `.pak` or `.skate` entries are bundled.
- On RedMagic 10 Pro (Android 16), `adb install -r` of the exact candidate returned `Success` over the previous code-5 build. The imported-asset completion marker, saved map and import-job preference hashes matched before and after. A cold launch displayed the 10-map setup list and rendered DownTown with the skater and touch controls visible. No fatal Android/native error appeared in the observed log.
- Before the version-only change, the same importer implementation completed a full 7.84 GB ISO conversion on this phone without a private `source.iso` copy. The source ISO remained untouched, StartPark rendered after import, and temporary work was removed. See [ISO storage and map validation](ISO_SPACE_START_MAP_VALIDATION.md).

## Remaining limits

The exact code-6 APK has not repeated the full ISO conversion. A streaming-only document provider, fresh install on another device, 16 KB page-size support and sustained performance remain unverified. The Retroid Pocket 3 Plus previously rendered StartPark but was killed loading University; this update does not make a broad compatibility claim. Check the GitHub-downloaded APK against the hash and certificate above.
