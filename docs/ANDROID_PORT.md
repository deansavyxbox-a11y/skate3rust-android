# Android setup and development

## Install on a phone (Windows PC required)

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

Install Git, Rust stable with the `aarch64-linux-android` target, Visual Studio C++ build tools, Java 17, Android SDK Platform 35 and NDK 29.0.14206865. Rust dependencies use edition 2024. The Gradle wrapper supplies Gradle 8.13 and Android Gradle Plugin 8.9.2. Android SDK licenses must be accepted.

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
