# Skate3Rust Android 0.2.0-rc2

Experimental prerelease, package `com.skate3rust.android`, version code 3. Includes the [0.2.0-rc1 importer changes](RELEASE_NOTES_0.2.0_RC1.md), plus a default-spawn correction for the verified DownTownSkatePark full layouts.

The original default locator is below the full park's raised platform. This candidate moves the Android default to a clear point on top of that platform without rewriting imported assets. The same correction applies at startup and default map reload; explicitly selected named destinations retain their existing behavior. See [spawn investigation and validation](DOWNTOWN_SPAWN_VALIDATION.md).

On 2026-10-16, the user confirmed the repaired spawn looks good on their RedMagic 10 Pro. This is user-reported acceptance of that spawn, not exhaustive gameplay or other-device validation.

Install over the existing app to retain assets, settings and touch controls. The existing signer, minSdk 26, targetSdk 35, ARM64 ABI and Vulkan requirements are unchanged. No game assets are bundled. Only RedMagic 10 Pro on Android 16 has been tested by this project; no universal Android compatibility is claimed.

The embedded FreeType library still has 4 KB ELF alignment; 16 KB support is not cleared. Clean-device installation and interrupted-import retry still need dedicated device validation before a broader compatibility claim. The reported viewer installation failure remains unexplained without that device's ADB error. All other [candidate limitations](RELEASE_CANDIDATE_0.2.0_RC1.md) remain unless superseded by a later validation report. The exact release APK SHA-256 is `54d3f9c5ca15ff6a03887eefe8adc7f287b402e9818208815b59f8df54bf7961`.
