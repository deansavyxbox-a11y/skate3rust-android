# Public APK compatibility audit — 2026-10-14

Historical audit of the exact published `0.1.0-experimental` artifact. For subsequent importer candidate packaging and update tests, see [0.2.0-rc1 validation](../android/RELEASE_CANDIDATE_0.2.0_RC1.md). Findings below remain scoped to the older artifact.

## Conclusion

The exact public APK passes download checksum, ZIP CRC, Android signing verification and static native alignment checks. No defective APK or reproducible installation failure has been established. The viewer's “App not installed” report remains unresolved: their device details, installed-package state and package-manager failure code are unavailable.

ADB initially returned an empty device list. After reconnection, the exact downloaded APK was successfully reinstalled on the RedMagic 10 Pro (`adb install -r`, exit 0, `Success`). The previously installed APK was byte-for-byte identical to the public download. Saved touch controls, four settings files and the asset completion marker were unchanged. Launcher startup returned `Status: ok`. Fresh installation, cross-version upgrade, conflicting-signer installation and full gameplay regression tests remain **not run**.

Only documentation was changed. No SDK, Vulkan, signing, package identifier, native code, gameplay, touch layout or installed device data was changed. No release was published. Unfinished importer work was left in its original checkout; this audit uses a separate branch from `origin/android-port`.

## Artifact identity

- Repository: `deansavyxbox-a11y/skate3rust-android`.
- Release: [`android-v0.1.0-experimental`](https://github.com/deansavyxbox-a11y/skate3rust-android/releases/tag/android-v0.1.0-experimental), published 2026-10-14 00:35:08 UTC.
- Source reviewed: `android-port` / release tag at `508035eccbcf5205ef5a46ea905987b2e5ee1111`. This is source inspection, not proof of a reproducible source-to-binary build.
- Downloaded asset: `skate3rust-android-polished.apk`, **50,698,701 bytes**.
- SHA-256: `9302b40f1ac7a8e1c055aa178fb0d91fede88a2177552bd40a4e16435153e733`.
- Hash independently matches both GitHub's asset digest and published `SHA256SUMS.txt`.
- Package `com.skate3rust.android`; version code **1**; version name `0.1.0-experimental`.

## Exact published APK checks

| Check | Result |
| --- | --- |
| Python `zipfile.testzip()` | Pass; no entry CRC failure |
| `apksigner verify --verbose --print-certs` (Build Tools 35.0.0) | Pass; one signer, APK Signature Scheme v2 valid |
| Other signature schemes | v1/v3/v3.1/v4 absent; not by itself a defect for this API 26+ APK |
| Certificate | `C=US, O=Android, CN=Android Debug`; RSA 2048 |
| Certificate SHA-256 | `82f0ecd863190a2e9224ad2be18455552a7c7804395d6949a80d4b701c7a4a81` |
| SDK metadata | min 26 (Android 8), target/compile 35 |
| ABI | Only `arm64-v8a`; both libraries are ELF64 AArch64 |
| Debug/native packaging | `debuggable=true`, `extractNativeLibs=true`; both native libraries ZIP-deflated |
| ZIP alignment | `zipalign -c -P 16 -v 4` passes; compressed native entries are extracted, not directly mapped |
| ELF alignment | Every LOAD segment in both libraries has alignment `0x4000` (16 KB) |
| Native dependency packaging | Main library needs `libc++_shared.so`, which is present; other DT_NEEDED entries are Android system libraries |
| Contents | Two DEX files, two native libraries, manifest, application branding/resources and Gradle metadata; no separate disc/map/texture asset payload |

Native libraries (uncompressed): `libskate3rust_android.so` **166,599,632 bytes** and `libc++_shared.so` **9,290,184 bytes**. Their combined size is 175,889,816 bytes; APK download size is not the installed-space requirement. No universal minimum free-space claim is made because installation staging and device behavior vary.

16 KB ZIP/ELF alignment checks pass. This does **not** establish runtime operation on a 16 KB page-size device; that requires device/emulator testing and checking runtime assumptions. See Android's [16 KB page-size guidance](https://developer.android.com/guide/practices/page-sizes).

## Source configuration and Vulkan

`android/app/build.gradle` fixes ARM64, minSdk 26 and targetSdk 35, enables debugging for the debug variant and uses legacy (compressed/extracted) native packaging. `android/build-rust.ps1` uses the API 26 ARM64 linker, a Rust release profile by default, `-z,max-page-size=16384`, and copies the needed C++ shared runtime. An Android debug APK can therefore contain optimized Rust code; these are separate build settings.

The decoded published manifest and source both require `android.hardware.vulkan.level` with feature-level value **0**. This is a Vulkan hardware feature level, not an API version number. There is no explicit `android.hardware.vulkan.version` declaration. Both diagnostic and gameplay render paths select `Backends::VULKAN`; gameplay uses Bevy's default device features (with optional profiling features). The source pins wgpu 27.0.1. Manifest filtering alone cannot express the complete renderer feature/limit/driver requirements. No verified minimum Vulkan API version or broad GPU compatibility is claimed by this audit. No OpenGL fallback was found in these selected render paths.

ABI/SDK/signature failures occur at installation. Vulkan adapter/feature/driver failures can occur after installation succeeds. Do not attribute a generic installer message to Vulkan without package-manager evidence.

## Signing and public-release reliability

The APK is validly debug-signed. This fact alone does not explain a fresh-install rejection. Android updates normally require a compatible signing identity; another developer's debug key for this same package can cause `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. The installed certificate on the viewer's device has not been obtained, so this is a hypothesis, not a confirmed finding. See [Android app signing](https://developer.android.com/studio/publish/app-signing).

The published source has no configured durable production signing workflow. Its documented asset installation currently depends on `run-as` and a debuggable application. Switching to a non-debuggable build would require an asset-installation alternative. Switching signing keys without a validated migration path can prevent updates to existing installations and lead users to uninstall, losing private assets/settings/layouts. Do not silently replace the signer.

The repository has a Windows release workflow but no Android artifact-validation/release workflow. Version code is currently 1. These are confirmed release-process gaps, not proof that this APK cannot install.

## Installation test matrix

| Scenario | Result / actual code |
| --- | --- |
| Downloaded APK static verification | Pass as recorded above |
| ADB device enumeration | RedMagic NX789J connected after initial empty enumeration |
| Fresh install on compatible ARM64 device | Not run; no failure code |
| Reinstall exact APK over same signer | PASS: streamed install, exit 0, Success; no failure code |
| Upgrade from previous distributed build | Not run; installed APK matched the public APK exactly, so this was a same-version reinstall |
| Deliberate different-signer conflict | Not run; no failure code |
| Launcher after reinstall | PASS: cold launch, Status: ok, reported total time 287 ms; process remained running at subsequent check |
| Saved preferences/settings | PASS: six saved files unchanged by byte comparison |
| RedMagic gameplay, touch/controller, maps after installation | Full interactive regression not run in this audit; earlier user reports were positive |
| Android API 26 and 16 KB page-size runtime | Not run |
| Viewer device | Model, OS, ABI, existing package and exact failure unknown |

No confirmed installation bug was fixed. Documentation now explains checksum verification, supported ABI/SDK, data-preserving update intent and error-code-specific troubleshooting.

## RedMagic device follow-up

Device: RedMagic 10 Pro / NX789J, Android 16 (API 36), `arm64-v8a`, 4096-byte memory pages, about 10 GB available on `/data`. Declared Vulkan features: hardware level 1, API version integer 4206592 (1.3.0), compute. Feature declarations do not establish full rendering compatibility.

Before reinstall, the installed base APK was pulled and its SHA-256, size and certificate matched the public APK exactly. Version code was 1. Preferences, installation settings and the completion marker were backed up locally. No game-asset payload was copied or distributed. The exact downloaded APK was then installed with `adb install -r`:

```text
exit_code=0
Performing Streamed Install
Success
```

Afterward, byte comparisons confirmed preservation of all six files: `touch_controls.xml`, `default-map.json`, `graphics.json`, `character.json`, `gameplay.json`, and `complete.json`. No uninstall or clear-data command was used. `am start -W -n com.skate3rust.android/.LaunchActivity` returned `Status: ok`. Full asset contents were not rehashed and interactive gameplay was not retested.

This establishes successful same-version reinstall on this phone, not a fresh install, cross-version upgrade, or 16 KB runtime test. The viewer has not responded with device details; no actual installation failure code has been obtained. Evidence files and the private settings backups remain outside the repository under the local audit output directory.

## Viewer model follow-up — 2026-10-16

The user subsequently received two reported device names: **vivo 1726** and **Samsung Galaxy A6+**. The reports do not yet include an Android version, exact A6+ model/firmware variant, installed-package state, attempted APK filename, APK checksum or ADB `Failure [...]` code. They are leads, not confirmed causes of either installation failure. The original audit above remains a dated record of what was known on 2026-10-14.

The vivo 1726 is commonly identified as a Y83 Pro; [vivo lists that model with Android 8.1 and a Helio P22](https://shop.vivo.com/in/product/10037), which would meet the APK's API 26 floor. That does not establish this viewer's supported ABIs or Vulkan feature declaration. The [Galaxy A6+ shipped with Android 8.0 and Snapdragon 450](https://news.samsung.com/br/samsung-apresenta-galaxy-a6-no-brasil), also meeting the SDK floor as sold. A [Samsung Members report from an A6+ owner](https://r2.community.samsung.com/t5/Galaxy-A/About-Samsung-galaxy-A6-32-bit-Android-Pie-Software/m-p/1639647/highlight/true) describes a 32-bit Android build; if the viewer's device reports only `armeabi-v7a`, an ARM64-only APK cannot install even though the processor itself is 64-bit. This remains a hypothesis until checked on that device. Neither model name establishes the required Vulkan hardware level 0.

Ask each viewer for their attempted release filename, Android version, whether an older `com.skate3rust.android` app is installed, and the complete output of:

```text
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
adb shell pm list features
adb install -r skate3rust-android-0.2.0-rc2-arm64.apk
```

The last command should use the actual downloaded APK filename. Redact device serials and private data before posting. If `arm64-v8a` is absent, `INSTALL_FAILED_NO_MATCHING_ABIS` is expected for the current release. If ARM64 is present, retain the actual install failure code and investigate signing, version, policy, storage or package parsing accordingly. A successful install followed by graphics failure is a separate Vulkan/runtime issue. Do not change SDK, Vulkan or signing requirements based solely on these model names.

## Pending device procedure

1. Connect and authorize ADB; record `adb devices -l`, model, SDK, `ro.product.cpu.abilist`, `getconf PAGE_SIZE`, available internal storage and Vulkan feature declarations. Redact serial numbers from public reports.
2. Record `adb shell dumpsys package com.skate3rust.android` and `adb shell pm path com.skate3rust.android`. If present, pull its base APK and verify its signer/version before trying an update. Preserve the user's settings, custom touch layout and assets; do not clear or uninstall their working installation.
3. If the signer/version permit a normal update, run `adb install -r skate3rust-android-polished.apk` and retain the complete exit status/output. If an installed newer build would be downgraded, stop and use a separate test device instead.
4. Fresh-install on a clean compatible test device. A secondary Android user alone does not isolate package signing/version state, because the APK is shared across users. Do not uninstall the user's working copy to manufacture this test.
5. Verify launcher, asset detection, map loading, rendering, saved touch layout, simultaneous touch input, USB controller connection/disconnection and respawn. Record launch failures separately from installation failures.
6. Repeat update testing with the actual next release artifact, then download it back from a draft/public artifact location and check bytes/certificate. Do not substitute a locally rebuilt APK for the shipped artifact.

## Recommendations for the next public APK

1. Establish ownership, secure backup and stable use of the signing key; record its public fingerprint. Explain and validate any key migration before changing it. Never commit a private keystore.
2. Increment versionCode for the next release and test an update from this exact public version, preserving settings/layout/assets. Do not publish a second different build with ambiguous identical version metadata.
3. Add an Android prepublication gate: ZIP integrity, SHA-256, apksigner validation, expected certificate, ABI/SDK/manifest checks, native dependency and 16 KB ELF/ZIP checks; retain exact artifact identity and test results.
4. Test clean install and upgrade on the RedMagic and at least one other compatible ARM64 device. Include 16 KB testing and older supported Android versions before expanding compatibility claims. Request the viewer's precise ADB error to target their failure.
5. Keep Vulkan and SDK requirements unchanged until representative tests justify a change. Android 8 is the manifest installation floor, not evidence of successful gameplay across Android 8 devices.
6. Evaluate stripping nonessential native symbols to reduce download/extracted size, retaining private symbol files for debugging. The C++ library contains debug sections and the main library retains symbol tables. Validate loading, crash symbolication and gameplay before shipping a packaging change.
7. Keep proprietary game assets outside the repository and release. Document asset setup independently of APK installation.

## Reproduction tools

Audit tools: Android Build Tools 35.0.0 (`aapt`, `apksigner`, `zipalign`), NDK 29.0.14206865 (`llvm-readelf`), Python 3.13 ZIP validation, PowerShell SHA-256 and GitHub releases REST metadata.

```powershell
Get-FileHash .\skate3rust-android-polished.apk -Algorithm SHA256
apksigner verify --verbose --print-certs .\skate3rust-android-polished.apk
aapt dump badging .\skate3rust-android-polished.apk
aapt dump xmltree .\skate3rust-android-polished.apk AndroidManifest.xml
zipalign -c -P 16 -v 4 .\skate3rust-android-polished.apk
# Extract the two lib/arm64-v8a/*.so entries before inspecting:
llvm-readelf -h -l -d .\libskate3rust_android.so
llvm-readelf -h -l -d .\libc++_shared.so
adb devices -l
```

Commands assume these SDK/NDK executables are on PATH. Local audit evidence includes release metadata, checksum file, downloaded APK, ZIP inventory, decoded manifest, signing output and ELF headers/program headers/dependencies. APKs and extracted libraries remain outside the source checkout.
