# Android setup and development

## Install and import on the phone (0.2.0-rc4 prerelease)

Download `0.2.0-rc4` from the [official prerelease](https://github.com/deansavyxbox-a11y/skate3rust-android/releases/tag/android-v0.2.0-rc4) and verify its SHA-256 against the release's `SHA256SUMS.txt`. It offers prepared ZIP and direct ISO import on the phone. Update with the same signer without uninstalling; uninstalling or clearing storage removes imported assets and settings. See [installation troubleshooting](../README.md#if-android-says-app-not-installed).

1. Install the prerelease APK and launch Skate 3 Rust. Existing installations remain available through **PLAY**.
2. Choose one input:
   - **IMPORT PREPARED ASSETS (.ZIP)**: select an Android asset package prepared using the commands below. A source-code ZIP or arbitrary game-data ZIP is not this format.
   - **IMPORT XBOX 360 ISO**: select your own Xbox 360 `.iso` image. If Downloads hides it, use the picker sidebar → internal storage/device name → Download and browse to the file.
3. Tap **CONTINUE IMPORT**. Keep the app open, the phone powered, and enough internal storage available. Progress reports phases; conversion may take time without a byte total. The ISO is copied privately, extracted and converted on the phone. Internet access is not required for game-asset conversion.
4. Wait for **Import complete** and tap **PLAY**. First gameplay loading can take tens of seconds. The original ZIP/ISO is not deleted. Imported assets are checked before publication, and a previous generation is retained for **RESTORE PREVIOUS ASSETS**.

The full ISO test used a 7.84 GB source with about 38 GB free internal storage after placing the ISO on the phone. Private ISO copy, extracted disc, intermediates and new assets coexist during processing; storage varies by input. This is not a guaranteed minimum. Prepared ZIP import uses less conversion workspace. If a job fails, read its message and preserve the original input. After an interrupted process, Retry restarts conversion; it does not resume all intermediate work. Cancellation may wait for the current converter stage.

Development builds from `feature/iso-space-start-map` add a **STARTING MAP** picker before **PLAY**. Choose an installed map such as StartPark if a larger map stalls or runs out of memory during loading. The choice is saved when you tap **PLAY**. These builds also try to read a seekable ISO directly from Android's document provider, avoiding a second full-size ISO in app storage. Providers that expose only a stream still use a private copy, which is deleted after extraction. Extraction, conversion, and the new installation still need several GB of free internal storage. These changes are not part of the published rc4 APK.

### Prepare a ZIP on Windows

Prepare a fresh staging directory using the PC instructions below, including park supplements where required. Then, from the repository root:

```powershell
pwsh -File android/package-assets.ps1 -Staging C:\SkateStaging\android-assets-full -Output C:\SkateStaging\skate3rust-assets.zip
adb push C:\SkateStaging\skate3rust-assets.zip /sdcard/Download/
```

USB file transfer also works. Select this ZIP through the app; do not unzip it into Downloads. Keep prepared packages private. See [prepared ZIP test results](../android/IMPORTER_TEST_RESULTS.md) and [direct ISO results](../android/ISO_IMPORT_VALIDATION.md) for tested scope.

## Install using a Windows PC and ADB

1. Download the APK from this fork's Android prerelease. Install Android SDK Platform Tools and enable USB debugging on the phone. Authorize your PC. These scripts currently target Windows and one connected Android device.
2. Use the [upstream Windows build](https://github.com/SK8-ENGINE/skate-3-rust-engine/releases/tag/experimental) to prepare assets from your own Skate 3 Xbox 360 ISO or extracted `default.xex`. Keep the resulting Windows package and its `data/installation.json` and `data/installations/...` folders intact. This Android version was tested against upstream build 29; later converter formats are unverified.
3. Clone/download this fork. Install PowerShell 7. Set `ANDROID_HOME` to your Android SDK directory (the default is `%LOCALAPPDATA%/Android/Sdk`). Put `platform-tools` on PATH for the `adb` commands below.
4. Install the APK and stage assets in a fresh directory. Replace the example paths with your own:

```powershell
adb install -r C:\Downloads\skate3rust-android-polished.apk
pwsh -File android/prepare-assets.ps1 -Source C:\Games\skate3rust-windows-x64 -Destination C:\SkateStaging\android-assets -AllMaps
pwsh -File android/deploy-assets.ps1 -Staging C:\SkateStaging\android-assets -Archive C:\SkateStaging\android-assets.tar
```

Preparation validates the Windows installation. Deployment verifies transfer hashes and preserves live settings while keeping the previous installation for rollback. Allow space for staging and rollback (approximately three times the asset payload plus 512 MB free). Close the app before deployment; launch it after deployment finishes. Do not clear app data when updating.

### Full skate park layouts

MegaPark, IndustrialSkatePark, StartPark and DownTownSkatePark require supplemental exports to include their full authored park pieces. Without these supplements, the original base maps can appear mostly empty.

Install Python and the repository's asset-pipeline dependencies (`python -m pip install -r tools/requirements-setup.txt`), then export from your own extracted game directory, from the repository root:

```powershell
python -m tools.asset_pipeline.park_export --game-root C:\Games\Skate3-extracted --output C:\SkateStaging\park-layouts
pwsh -File android/prepare-assets.ps1 -Source C:\Games\skate3rust-windows-x64 -Destination C:\SkateStaging\android-assets-full -AllMaps -ParkLayouts C:\SkateStaging\park-layouts
pwsh -File android/deploy-assets.ps1 -Staging C:\SkateStaging\android-assets-full -Archive C:\SkateStaging\android-assets-full.tar
```

Keep each exported `.skate` package with its `.receipt.json`. The exporter requires the original extracted resources, including park assets; it cannot reconstruct them from the APK. Generated assets and staging archives must remain private.

## Controls

Use a USB controller, or the touchscreen sticks and action buttons. Touch controls hide when a physical controller is detected and return when disconnected. Open Menu → Graphics → Touch controls to move buttons and sticks. Save persists normalized positions, Cancel discards edits, and Reset changes the draft until saved.

## Build from source on Windows

Install Git, Rust stable with the `aarch64-linux-android` target, Visual Studio C++ build tools, Java 17, Android SDK Platform 35 and NDK 29.0.14206865. Install Python 3.13 through the Windows Python launcher (`py -3.13`) for Chaquopy's build step. Rust dependencies use edition 2024. The Gradle wrapper supplies Gradle 8.13 and Android Gradle Plugin 8.9.2; Chaquopy 17.1.0 supplies the embedded Android Python runtime. Android SDK licenses must be accepted. The initial build downloads dependencies.

```powershell
rustup target add aarch64-linux-android
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
$env:ANDROID_NDK_HOME = "$env:ANDROID_HOME\ndk\29.0.14206865"
cd android
.\gradlew.bat assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`. Rust is compiled in release profile inside a debuggable Android package. Builds use the local Android development signing key; never commit signing keys or `local.properties`. Linux/macOS host builds are not currently provided by these PowerShell/Windows NDK scripts.

## Validation and limits

```powershell
pwsh -File android/test-touch-controls.ps1
pwsh -File android/test-map-starts.ps1
pwsh -File android/test-asset-validation.ps1
python -m unittest tools.asset_pipeline.test_park_layout tools.asset_pipeline.test_park_resources tools.asset_pipeline.test_park_export
```

The port was tested on RedMagic 10 Pro, Android 16. User testing confirmed map selection, repaired parks, landings, rails, respawn, multitouch and controller switching. Layout Save/Cancel/Reset and native loading/error states were also checked. This is not exhaustive device or gameplay coverage. Existing unrelated upstream example/physics test failures mean full-suite success is not claimed.

Park decals/secondary UV materials remain incomplete. Dynamic park objects are baked at initial placements. No proprietary assets are provided. The desktop update system is not an Android APK updater.
