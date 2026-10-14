# Skate3Rust Android

Experimental native ARM64 Android port of [SK8-ENGINE/skate-3-rust-engine](https://github.com/SK8-ENGINE/skate-3-rust-engine), built with Rust and Bevy. Based on upstream build 29 (`60efdef86600d8d8d4feb4b7c608fa0efd0643d7`).

[Download the Android APK](https://github.com/deansavyxbox-a11y/skate3rust-android/releases/tag/android-v0.1.0-experimental) · [Setup and build guide](docs/ANDROID_PORT.md) · [Contributing](CONTRIBUTING.md)

## What works

- Native Android rendering and skating, map selection, collision and rails.
- Ten converted disc maps, with supplemental full layouts for four skate parks.
- USB controller support and multitouch sticks/buttons, with automatic controller detection.
- Movable touch controls: Menu → Graphics → Touch controls. Drag controls, then Save. Cancel discards edits; Reset previews defaults until saved.
- Android launcher icon and branded loading screen with native loading status.

Tested on a RedMagic 10 Pro (Android 16, Adreno 830). Other devices are unverified. Requires ARM64, Android 8 or newer and compatible Vulkan support; this is not a compatibility guarantee.

## Before downloading

**The APK does not include Skate 3 game assets.** You need your own Xbox 360 game copy, a Windows PC to prepare the assets, and ADB to install them. There is currently no on-phone asset importer. Installing only the APK does not provide the original maps or characters.

Follow the [setup guide](docs/ANDROID_PORT.md). Do not upload ISOs, extracted game files, converted maps, textures or other proprietary assets to this repository or its issues.

This is an experimental, debuggable APK because the current asset installer uses Android `run-as`. It uses a development signing certificate. Contributor builds may have different certificates and cannot necessarily update an existing install. Uninstalling clears app-private game assets, settings and touch layouts; back up your data first.

## Known limitations

Gameplay parity remains in progress. Some park decals/materials are incomplete; dynamic park objects use static initial placements. Audio parity, wardrobe expansion, multiplayer, broad device compatibility and sustained thermal performance are not established for Android. There is no automatic Android updater. See the guide for validation scope.

## Source and attribution

Original engine and reverse-engineering work: [SK8-ENGINE and upstream contributors](https://github.com/SK8-ENGINE/skate-3-rust-engine). See [the original README](README-UPSTREAM.md), [GPL-3.0 license](LICENSE), and notices retained in dependency/vendor folders. The root license is preserved from upstream's current main branch; third-party notices continue to apply. Android port work is published in this fork with its source.

This is an unofficial fan project, not affiliated with or endorsed by EA, Black Box, Full Circle or the Skate franchise. Game names and trademarks belong to their respective owners. No proprietary game content is included in the Android release.
