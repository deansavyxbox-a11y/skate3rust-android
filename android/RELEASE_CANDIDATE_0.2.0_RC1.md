# Android 0.2.0-rc1 candidate validation — 2026-10-15

## Status

Prepared locally and installed on RedMagic 10 Pro; not pushed or published. This is a review candidate, not a broad compatibility claim. Existing public `0.1.0-experimental` remains unchanged. Its viewer-reported installation failure is unresolved because no viewer device details or package-manager failure code were supplied.

## Exact candidate artifact

- File: `skate3rust-android-0.2.0-rc1-arm64.apk`.
- Bytes: **78,123,908**.
- SHA-256: `d26bb8507f9a35dda2f777d8a659406a00daf92ae5a5d2ff4a268f4c9dc0ce8e`.
- Package: `com.skate3rust.android`; versionCode **2**, versionName **0.2.0-rc1**.
- Signer SHA-256: `82f0ecd863190a2e9224ad2be18455552a7c7804395d6949a80d4b701c7a4a81` (existing development certificate, unchanged).
- minSdk **26**, target/compile **35**, native ABI **arm64-v8a** only.
- Vulkan hardware level feature **0**, required, unchanged; this is a hardware feature level, not a minimum Vulkan API version. No alternate graphics backend added.
- Debuggable, extracted native libraries; optimized Rust release profile. Retaining debugging preserves the PC `run-as` asset setup path. A future signing or non-debuggable migration requires separate planning and update tests.

The candidate contains the embedded Python converter runtime, making it larger than the previous roughly 51 MB public APK. APK entries total 216,761,442 uncompressed bytes; nested Python extraction, Android staging and asset conversion add space beyond this figure. It is not an installed-space estimate.

## Packaging checks

- APK and nested ZIP/IMY archives: CRC checks pass.
- `apksigner verify --verbose --print-certs`: exit 0, existing certificate; valid APK v2 signature.
- `aapt` manifest/badging: expected version, SDK, ABI, Vulkan declaration and setup launcher.
- `zipalign -c -P 16 -v 4`: exit 0. This checks ZIP alignment, not every embedded ELF.
- Inspected **99 ELF libraries**, including Python runtime and nested wheels, all ELF64 AArch64. Every DT_NEEDED name is supplied in the artifact or the checked Android system-library set; this is a static name check, not proof of runtime loader behavior on every OS.
- **98/99** libraries have LOAD alignment at least 16 KB. `assets/chaquopy/requirements-common.imy!/chaquopy/lib/libfreetype.so` has **4 KB** LOAD alignment. The candidate is **not cleared for 16 KB devices**. Rebuild/replace this dependency and validate the converter and gameplay on a 16 KB device before claiming that support. No dependency upgrade or SDK change was made to conceal this gap.
- Gameplay/import JNI libraries and packaged C++ runtime pass 16 KB static alignment checks.
- Inventory contains app branding, converter source/metadata and dependency runtime; no ISO, XEX, converted `.skate` maps, game asset payload or private signing keystore was packaged. Chaquopy's `cacert.pem` is a public CA bundle, not a private signing credential. User settings, game data and raw device logs remain outside the source/release directory.

## Tests and device installation

Device: RedMagic 10 Pro / NX789J, Android 16/API 36, ARM64 userspace, **4096-byte** pages. Serial omitted.

| Test | Result |
| --- | --- |
| Full optimized Rust build and Android APK assembly | Pass, exit 0 |
| Final Java importer tests | **27 tests**, 0 failures/errors |
| Python importer/exporter/runtime/RefPack tests | **10 tests**, pass |
| PowerShell prepared ZIP packaging fixture | Pass |
| Reinstall exact previous public APK as update-test baseline | `adb install -r`: exit 0, `Success`; public artifact hash `9302b40f1ac7a8e1c055aa178fb0d91fede88a2177552bd40a4e16435153e733`, versionCode 1 |
| Upgrade exact public baseline → candidate versionCode 2 | `adb install -r`: exit 0, `Success` |
| Reinstall exact candidate | `adb install -r`: exit 0, `Success` |
| Pull installed candidate base APK and compare | Exact SHA-256 match with candidate above |
| Saved data after upgrade and reinstall | All **9** captured files byte-identical after each operation |
| Setup launcher | Cold launch `Status: ok`, assets detected, Play enabled |
| Play/native startup | DownTownSkatePark selected from the user's existing saved choice; Vulkan renderer and simulation running, `simulation_failed=false`, touch overlay present; touch push changes player position |
| Startup ANR | No new ANR in this check; last recorded ANR remains the earlier 19:25:57 EDT event before the prior focus fix |
| Fresh installation, conflicting-signer test | Not run; no separate clean compatible device available. Did not uninstall or clear the user's app |
| Other devices, API 26 gameplay, 16 KB runtime | Not tested |

The nine preserved files are touch preferences, importer job preferences, Chaquopy preferences, graphics/default-map/character/gameplay settings, and the active/previous generation completion markers. Full game assets were not rehashed in this update test; no asset import was started. The previous public APK was installed only as the baseline and was not launched against the newer assets.

The current DownTownSkatePark spawn showed the camera very close to/inside the character during the startup spot check. The environment was present and simulation continued; this is **not a clean visual gameplay acceptance for that spawn**, and whether it predates this candidate has not been isolated. No camera, spawn or physics code changed during candidate preparation. Record it for a focused follow-up before treating this as ready for broad publication. Other map/controller acceptance comes from the preceding ISO validation and the user's subsequent several-map/controller test, not an exhaustive final-candidate rerun.

## Reliability fix from review

An independent review found that process death during ISO conversion bypassed worker cleanup, leaving the previous job's multi-GB workspace behind on retry. The serialized import worker now reclaims the exact prior job-owned pending directory after journal recovery and before allocating a new one. It validates the identifier and canonical parent, does not follow symbolic links, and never removes active/previous installations or unrelated directories. A separate durable abandoned-job ID survives cancellation/failure until reclamation succeeds.

Two new regression tests cover removal while preserving installed/unrelated data and journal recovery before removal. The new tests first failed because the cleanup method was absent, then passed after implementation. Follow-up review found no remaining important findings in the fix. On-phone process-kill/retry and full ISO conversion were not repeated solely for this final cleanup change; this remains a targeted device validation item before publication.

## Remaining release gates

1. Investigate/retest the observed DownTownSkatePark camera/spawn view, and repeat full user gameplay acceptance on the exact candidate.
2. Exercise interrupted-import cleanup/retry on-device, with source and both installed generations preserved.
3. Fresh-install on another clean compatible ARM64 device; do not erase the working RedMagic copy. Obtain the viewer's exact ADB error if they respond. No `INSTALL_FAILED_*` occurred in the successful installs above.
4. Rebuild/replace the 4 KB FreeType dependency and test on 16 KB hardware before expanding support claims.
5. Review and publish corresponding source and attribution with any public APK; securely retain the existing signing key outside Git. Never distribute proprietary game assets or private device evidence.
6. For any uploaded release artifact, download it back and compare its bytes, certificate and metadata with this report. A local candidate test does not validate a future uploaded file automatically.

Local evidence is held under `outputs/android-0.2.0-rc1/private-evidence`; only the selected APK, checksum and sanitized reports/notes belong in the distributable set. See [release notes](RELEASE_NOTES_0.2.0_RC1.md), [ISO test scope](ISO_IMPORT_VALIDATION.md), and [historical exact public-APK audit](../docs/APK_COMPATIBILITY_AUDIT.md).
