# DownTownSkatePark Android default spawn

The user confirmed the park is playable but the default load-in clips. The initial `0.2.0-rc1` startup spot check reproduced the close/clipped camera view.

## Cause and correction

The full-layout supplement replaces the base map's start with its authored locator at approximately `(66.052, 0, -64.023)`. Render geometry at this position includes the full park platform at `y=1.45`, above that locator. The character therefore starts beneath the platform rather than on its upper surface.

Android now uses `(80, 1.45, -70)` for the default start of the two verified PC/ISO full-layout packages, retaining heading 0. Eighteen surface samples across the chosen camera/push corridor found the 1.45-metre platform and no intervening render surfaces above it. Runtime physics and visual checks are required in addition to these geometric samples.

The correction is applied after catalog/base/supplement validation, to the in-memory map only. It is restricted to DownTownSkatePark, the original locator and the recognized supplemental fingerprints `875047220466072805` / `10763177649548943994`. Unknown revisions, other maps and modified starts remain unchanged. Startup and default map reload share this loader. Explicit named destinations are applied separately afterward.

No imported package, receipt, integrity manifest, map identity or user preference is rewritten. Vulkan, SDK, signer, touch/controller handling and rendering are unchanged. The revised APK is version code 3 / `0.2.0-rc2`; the original candidate artifact and report remain intact.

## Verification

- Two focused Rust tests first failed for the absent correction, then passed: known packages move to the chosen platform, while unknown packages, other maps and changed starts remain untouched.
- Independent review found no important issues in the correction.
- Full optimized Android/Rust build and APK assembly passed. Java importer suite: 27 tests, zero failures/errors. The two focused Rust tests pass.
- Exact APK: `skate3rust-android-0.2.0-rc2-arm64.apk`, 78,123,656 bytes, SHA-256 `54d3f9c5ca15ff6a03887eefe8adc7f287b402e9818208815b59f8df54bf7961`.
- APK signing/integrity/ZIP alignment checks pass with the original signer, minSdk 26, targetSdk 35 and ARM64 ABI. All 99 embedded ELF libraries are AArch64; the previously reported FreeType 4 KB alignment limitation remains. This candidate is not cleared for 16 KB devices.
- Update installation on RedMagic 10 Pro returned `Success`. All nine captured preference/settings/active-and-previous asset-marker files are byte-identical to the pre-install backup.
- **User device acceptance, 2026-10-16:** after checking the installed repaired build on the RedMagic, the user reported, “i check out the spawn . its good now”. This confirms the corrected default spawn looks good in their test and resolves the reported load-in clipping issue for this device/package. It does not establish separate instrumented cold-start/reload, physics telemetry or exhaustive landing/grinding/respawn coverage.
- Earlier automated visual verification stopped at the locked phone. The black locked-screen capture and stale runtime log were not counted as gameplay evidence. The acceptance above is explicitly the user's subsequent test.

The RC2 prerelease uses the exact APK recorded above. RC1's artifact/report remain unchanged. On-device interrupted-import retry and clean-device installation remain untested; 16 KB support remains uncleared. Local audit evidence and private geometry probes remain outside the repository under `outputs/android-0.2.0-rc2` and `outputs/downtown-spawn-fix`; do not publish the geometry probes or user backups.
