# Skate3Rust Android

Experimental native ARM64 Android port of [SK8-ENGINE/skate-3-rust-engine](https://github.com/SK8-ENGINE/skate-3-rust-engine), built with Rust and Bevy. Based on upstream build 29 (`60efdef86600d8d8d4feb4b7c608fa0efd0643d7`).

[Download the Android APK](https://github.com/deansavyxbox-a11y/skate3rust-android/releases/tag/android-v0.2.0-rc3) · [Setup and build guide](docs/ANDROID_PORT.md) · [Contributing](CONTRIBUTING.md)

## What works

- Native Android rendering and skating, map selection, collision and rails.
- Ten converted disc maps, with supplemental full layouts for four skate parks.
- USB controller support and multitouch sticks/buttons, with automatic controller detection.
- Movable touch controls: Menu → Graphics → Touch controls. Drag controls, then Save. Cancel discards edits; Reset previews defaults until saved.
- Android launcher icon and branded loading screen with native loading status.
- On-phone prepared ZIP import and direct conversion of your own Xbox 360 ISO. Both paths completed on RedMagic 10 Pro. Prepared ZIP import also completed on Retroid Pocket 3 Plus; direct ISO conversion there has not been tested. `0.2.0-rc3` shows live conversion steps and map counts instead of a misleading `0.0 MB` counter.

Gameplay was tested on RedMagic 10 Pro (Android 16, Adreno 830). On Retroid Pocket 3 Plus (Android 11), the exact public APK installed and StartPark rendered, but loading University triggered Android's low-memory killer. StartPark showed 9 FPS in one screenshot; sustained performance and controls were not tested on that device. Other devices remain unverified. Requires ARM64, Android 8 or newer and compatible Vulkan support; this is not a compatibility guarantee.

## Before downloading

**The APK does not include Skate 3 game assets.** You need your own Xbox 360 game copy. The `0.2.0-rc3` prerelease offers prepared ZIP import or direct ISO conversion on the phone. Installing only an APK does not provide the original maps or characters.

Follow the [setup guide](docs/ANDROID_PORT.md). Do not upload ISOs, extracted game files, converted maps, textures or other proprietary assets to this repository or its issues.

This is an experimental, debuggable APK because the current asset installer uses Android `run-as`. It uses a development signing certificate. Contributor builds may have different certificates and cannot necessarily update an existing install. Uninstalling clears app-private game assets, settings and touch layouts; back up your data first.

## Installing the public APK

Download `skate3rust-android-0.2.0-rc3-arm64.apk` and `SHA256SUMS.txt` from the [official prerelease](https://github.com/deansavyxbox-a11y/skate3rust-android/releases/tag/android-v0.2.0-rc3). GitHub's source-code ZIP is not an installable app. This APK supports **64-bit ARM Android installations (`arm64-v8a`), Android 8 / API 26 or newer**. A 64-bit processor running a 32-bit Android system is insufficient. Compatible Vulkan graphics are also required for gameplay; meeting the installation requirements does not guarantee rendering compatibility. Devices with 16 KB memory pages are not yet cleared for this build.

On the phone, open the APK and allow installation from the browser or file manager you used when Android requests it. Managed-device policies may restrict sideloading. Keep your device's security protections enabled; if installation is blocked, capture the message before changing anything.

For a Windows PC, verify the download and install with [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools):

```powershell
Get-FileHash .\skate3rust-android-0.2.0-rc3-arm64.apk -Algorithm SHA256
adb devices -l
adb install -r .\skate3rust-android-0.2.0-rc3-arm64.apk
```

Compare the hash with `SHA256SUMS.txt` from that same release. The `-r` option requests reinstall/update while retaining app data, provided the package and signing certificate are compatible. Do not uninstall or clear app storage as a routine update step: this removes imported assets, settings and your touch layout. APK installation and [game-asset setup](docs/ANDROID_PORT.md) are separate steps.

### If Android says “App not installed”

That message alone does not identify the cause. Use the ADB command above and include its complete `Failure [...]` output in an issue, together with the release filename, phone model, Android version and whether this was a first install or update. Do not attach game assets or private device information.

| ADB result / symptom | Meaning and next step |
| --- | --- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Usually an installed copy has a different signing certificate. Check whether it came from another contributor or your own build. Obtain a matching-signer update; preserve assets/settings before considering removal. A different Android user/profile may also retain the package. |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | This APK needs `arm64-v8a` Android userspace. Check `adb shell getprop ro.product.cpu.abilist`; a 64-bit processor can still run 32-bit Android. There is no ARM32 or x86 APK in this release. |
| `INSTALL_FAILED_OLDER_SDK` | Check `adb shell getprop ro.build.version.sdk`; this release requires API 26 or newer. |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | An installed copy has a higher version code. Use a compatible newer build; do not force a downgrade as a routine fix. |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | Free internal storage and retry. APK size is smaller than the installed footprint, particularly with extracted native libraries and the candidate's Python runtime. Game assets and import staging require additional space. |
| `INSTALL_PARSE_FAILED_*`, invalid APK, or a different checksum | Download the official APK again and verify its checksum; preserve the exact error if it persists. |
| `INSTALL_FAILED_USER_RESTRICTED` or device-policy warning | Check the device's sideloading/USB installation permissions or contact its administrator. |
| ADB says `Success`, but launch fails or maps are missing | Installation succeeded. Report launch logs and asset-setup status separately; GPU/driver support and missing assets require different investigation. |
| ADB says `Success`, but the game closes while loading a large map | Check `adb logcat` for `lowmemorykiller` and report device RAM and map name. On the Retroid Pocket 3 Plus, University was killed under memory pressure while StartPark rendered. This is a runtime limitation, not an APK installation error. |

These are troubleshooting possibilities, **not confirmed explanations for the reported viewer failure**. The [published APK audit](docs/APK_COMPATIBILITY_AUDIT.md) records the exact artifact checks, certificate fingerprint and current testing gaps. Debug signing alone does not establish that a fresh install must fail.

## On-phone setup (0.2.0-rc3 prerelease)

See the [step-by-step setup guide](docs/ANDROID_PORT.md). Choose **IMPORT PREPARED ASSETS (.ZIP)** for an Android package prepared on a PC, or **IMPORT XBOX 360 ISO** to convert your own disc image entirely on the phone. Keep the app open and the phone powered during conversion. The original file is retained; a new installation is validated before it replaces the active assets. **RESTORE PREVIOUS ASSETS** returns to the retained previous generation.

Direct ISO import is experimental and needs substantial free internal storage. The successful test began with about 38 GB free, with the source ISO already on the phone; this is a tested condition, not a universal space requirement. If the ISO is missing from the Downloads picker, open the picker's sidebar and browse internal storage → Download. If Android stops the job, reopen the app and retry; conversion restarts rather than resuming every intermediate step. Keep the source file until import succeeds. See [ISO validation and limitations](android/ISO_IMPORT_VALIDATION.md).

## Known limitations

Gameplay parity remains in progress. Some park decals/materials are incomplete; dynamic park objects use static initial placements. Audio parity, wardrobe expansion, multiplayer, broad device compatibility and sustained thermal performance are not established for Android. There is no automatic Android updater. See the guide for validation scope.

See the [Retroid Pocket 3 Plus validation](android/RETROID_POCKET_3_PLUS_VALIDATION.md) for the second-device installation, import and map-loading results.

This prerelease is not cleared for 16 KB page-size devices: its embedded FreeType library has 4 KB alignment. See the [initial candidate validation report and remaining release gates](android/RELEASE_CANDIDATE_0.2.0_RC1.md).

The [0.2.0-rc2 prerelease](android/RELEASE_NOTES_0.2.0_RC2.md) adjusts DownTownSkatePark's default start above the raised platform. The user confirmed the repaired spawn looks good on RedMagic 10 Pro; see the [spawn validation report](android/DOWNTOWN_SPAWN_VALIDATION.md) for scope and remaining checks.

The [0.2.0-rc3 prerelease](android/RELEASE_NOTES_0.2.0_RC3.md) improves the direct ISO import progress display. During conversion, it shows the current step and map count without claiming that zero bytes have been processed or estimating a percentage.

## Source and attribution

Original engine and reverse-engineering work: [SK8-ENGINE and upstream contributors](https://github.com/SK8-ENGINE/skate-3-rust-engine). See [the original README](README-UPSTREAM.md), [GPL-3.0 license](LICENSE), and notices retained in dependency/vendor folders. The root license is preserved from upstream's current main branch; third-party notices continue to apply. Android port work is published in this fork with its source.

This is an unofficial fan project, not affiliated with or endorsed by EA, Black Box, Full Circle or the Skate franchise. Game names and trademarks belong to their respective owners. No proprietary game content is included in the Android release.
