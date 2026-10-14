# On-phone Android asset import design

Date: 2026-10-13
Status: Written design for user review; implementation has not started.
Base: Android port commit 508035eccbcf5205ef5a46ea905987b2e5ee1111.

## Intent and agreed scope

Players should install the APK, choose their own files on the phone, and reach gameplay without ADB. Offer both a prepared asset archive and an Xbox 360 ISO. Archive preparation may still use a PC; ISO conversion must run locally on the phone without a PC, server, external Python installation or proprietary downloads. Preserve existing settings and the user's custom touch layout. This feature does not distribute game assets.

Deliver the shared installer/archive path first, followed by the ISO converter. This sequencing does not reduce the agreed scope: both choices must work before the complete feature is described as finished. An intermediate archive-only build must say ISO support is still in development.

## Existing implementation and constraints

Android currently reads files/installation with complete.json, assets/, maps/ and settings/. prepare-assets.ps1 writes a SHA256 transfer manifest and default settings; deploy-assets.ps1 validates staging and swaps directories. LaunchActivity immediately starts the native game even when assets are absent. The native runtime is a process-wide Bevy application and is not a reusable conversion command.

The desktop pipeline already has Python/NumPy/Pillow parsers and direct .skate/GLB writers. Its installer also invokes child Python workers, a Windows extract-xiso download and the desktop executable's --check-assets mode. These orchestration steps cannot be used unchanged on Android. Existing build_map.py/Blender helpers are not the direct map writer used by install.convert_map and are not required for the selected path.

## User flow

On launch, perform installer recovery before loading the game library. With no usable installation, show the existing branding and two setup choices: Import prepared assets and Import Xbox 360 ISO. With an installation, show Play and Manage assets, including installation/map status. Provide touch and controller focus navigation.

Each choice opens ACTION_OPEN_DOCUMENT. Read the returned content URI, not a guessed filesystem path. Show the selected filename, available space, the operation's storage estimate and Start/Cancel. A filename extension alone is not format validation. Cloud/provider inputs must be readable completely or fail clearly.

During import show actual phases, bytes or item progress where measured, current map and Cancel. Use indeterminate progress for stages whose work is not known; do not fabricate a whole-job percentage or completion time. Success shows installed maps and Play. Error messages identify the phase and recovery action without dumping raw traces into the UI. Existing assets remain available after cancellation or failure.

Import is permitted only before the native game has initialized in the current process. There is no live hot-swap of files used by Bevy. If a running game is brought forward by relaunching the app, return to that session rather than permitting import. To change assets after playing, the user closes the current app session and reopens setup. Enforce this guard in code, including Activity recreation and external intents. GameActivity becomes non-exported so external launches cannot bypass setup. A future return-to-setup process split is outside this design.

## Components and state

- Setup UI: launcher gate, file selection, progress attachment, error/summary and Play. It does not execute conversion on the UI thread or initialize Vulkan during setup.
- Import coordinator: one active job, cancellation, preflight, persistent journal and lifecycle-independent progress. States are idle, inspecting, importing, converting, validating, publishing, complete, cancelled, failed and interrupted.
- Foreground import service: user-started dataSync service for import/local file processing. Visible notification provides progress and Cancel; request notification permission where applicable without making denied notification access a storage failure. Handle OS timeout by checkpointing/aborting safely and stopping the service. Stop on every terminal path. No boot-time restart.
- Prepared archive reader: bounded streaming extraction and transfer-manifest validation.
- ISO reader/converter: bounded disc filesystem reader plus adapted owned-game pipeline. Reports structured progress and produces the same staging contract as archive import.
- Installation store: staging, validation, settings preservation, publication journal and recovery. Both import modes must use this single publication path.

Only setup/import work runs while an import job owns the installation lock. Play is disabled until the job is terminal and publication/recovery is complete. Cancellation is deferred during the short publication critical section and cannot interrupt directory recovery halfway through.

## Prepared archive contract

Use ZIP, including ZIP64, as the documented portable format. Add a Windows packaging command that takes the already validated prepare-assets output and produces a ZIP with complete.json and transfer-manifest.json at its root. Existing ADB TAR archives are not accepted silently; explain the required ZIP format if one is selected. No automatic heuristic conversion of arbitrary Windows release ZIPs.

Manifest entries name relative paths with byte counts and SHA256 hashes. Strip an optional UTF-8 BOM in JSON from current PowerShell output. Require version 1 complete metadata, a bounded manifest, unique paths, consistent counts/total bytes, required runtime files and compatible map packages. Define compatibility using the existing SKATE reader version and the Android staging schema, not just a git revision string.

Reject absolute/drive/UNC paths, traversal segments, NUL/control characters, backslashes, duplicate normalized paths, links and entries outside the installation's allowed assets/, maps/, settings/ and metadata roots. Bound entry count, name length, manifest size, declared and actual expanded bytes; enforce limits while streaming even when ZIP header sizes are unknown or false. Reject unlisted payloads and missing entries. Hash and size every payload before publication. SHA256 establishes file integrity, not publisher authenticity; imports remain user-selected local data and are never executed as code.

The source archive is never deleted. Stream it into a private staging directory without retaining a second full compressed copy. On provider errors discard only this job's uncommitted output. Do not extract by shell command.

## ISO conversion approach

Reuse the existing owned-game parser/converter source with an embedded Python runtime and Android ARM64 NumPy/Pillow support. Gameplay continues to run in native Rust/Bevy. Adapt conversion functions to in-process calls; do not invoke Windows executables, download tools at runtime, spawn a desktop Python interpreter or bundle Blender. This minimizes divergence from the currently working PC conversions. A full Rust rewrite would duplicate the large conversion stack and is not the initial approach. Remote conversion would require uploading private game files and is not selected.

Package a source-built native/Rust XDVDFS reader through a library interface. Validate volume signatures, partition offsets, directory/tree bounds, file extents, integer overflow, names and extraction budgets. Support structurally validated Xbox 360 XDVDFS ISO images of the user's own game; compressed/split archives, encrypted/unsupported images and unrelated titles fail with a specific message. Validate required Skate 3 resources and default.xex metadata rather than trusting the extension. Do not claim every regional/revision image is supported without tests.

Use a seekable provider descriptor when available. For non-seekable providers, explain the additional ISO-sized private copy and include it in the space estimate before copying. Never modify the selected image. Extract only required game resources to job-owned storage and remove intermediates when no longer required.

Convert core stock graphs, controls/physics data, skater and animations, HUD/environment data, all discovered supported disc maps and the four supplemental park layouts. Preserve the stock-skater scope of the existing Android build. Process maps serially initially and release arrays/caches between jobs; avoid desktop multi-map subprocess concurrency. Adapt desktop --check-assets validation to an Android-callable Rust validator that does not start Bevy/Vulkan. Apply Android map-start fixes and receipt bindings consistently with the existing preparer. Produce the same manifest/complete metadata as the archive path.

Before promising a public ISO build, prove embedded dependency packaging, direct ISO extraction and one complete University conversion on the phone. Then validate all ten maps and the four park layouts. If any dependency or resource constraint blocks this approach, report the specific evidence and revise this portion of the design; do not replace ISO conversion with a nonfunctional button or remote service.

## Storage, preservation and recovery

Use app-private, non-backed-up installation storage. Retain the existing files/installation layout for runtime and ADB compatibility. Write only to a unique job staging directory until validation completes. Preflight estimates include converted payload, required extracted intermediates, optional private ISO copy, existing installation/rollback storage and safety headroom. Recheck free space at phase boundaries and fail safely on ENOSPC. Do not promise a fixed GB requirement until measured on the device.

Before replacing a working installation, copy current settings into validated staging after validating imported settings defaults. Existing touch SharedPreferences remain untouched. Preserve compatible saved map selection; if its map is absent, select an installed fallback and tell the user. Do not copy an imported archive over live preferences.

Publication uses a durable journal: mark validated pending; preserve current installation as rollback; publish pending by same-filesystem rename; mark committed. A filesystem rename pair is not a single transaction: startup recovery must handle every intermediate state before Play. Flush files/journal as needed, and only advance a journal step after its operation succeeds. Never delete the only recoverable installation. Retain one rollback generation and offer Restore previous assets when available; remove older rollback only after a successful new commit and explicit retention handling. Tests must cover both existing ADB installations and importer-created ones.

On process death, mark the job interrupted, recover publication first, and offer Retry. Initial version restarts unfinished conversion/extraction rather than claiming byte-level resume. Reuse only independently validated completed work if a later implementation explicitly supports it. Cleanup is constrained to canonical job-owned directories. Preserve the user's source files and prior installation on every failure path.

## Validation and release acceptance

Use generated fixtures in public tests, never game assets. Test path traversal, duplicate names, malformed JSON, invalid hashes/counts/sizes, ZIP64, truncation, inflated entries, unsupported versions, provider failure, insufficient storage, cancellation and publication recovery at every journal boundary. Exercise XDVDFS parser bounds with synthetic images and malformed directory trees. Test progress/cancellation without booting the game.

Compare desktop and Android outputs from the same privately owned source using hashes where deterministic and parsed structure/geometry/rail counts otherwise. Run existing touch/layout/loading-state, map-start and park-export regressions. On the RedMagic, test archive selection/import, ISO selection/conversion, background/foreground changes, interruption, fresh setup, replacement and rollback. Verify custom controls, uncapped graphics settings, map selection and controller switching remain intact. Never uninstall or clear the user's current app data for a fresh-install test; use an isolated test application ID/data directory or secondary test device.

The completed feature requires both modes to reach playable gameplay, with ten maps and visible full layouts for the four parks on the supported test source. Record measured peak temporary storage, conversion time, memory behavior and clear device/revision limits. Unsupported sources must leave existing gameplay intact. Do not claim broad device compatibility from one RedMagic test.

Increase Android versionCode and versionName for the importer release and retain the existing signing identity locally. Do not publish signing keys. Prepared ZIPs/ISOs/generated game assets remain private. Publish source, APK, checksums, instructions and original test fixtures only. Keep the current public release available until replacement passes acceptance. Update README to distinguish the archive PC-preparation path from entirely local ISO setup. Choose a non-debuggable public APK only after both import modes work without run-as; retain development tooling separately.

## References

- Android document picker: https://developer.android.com/training/data-storage/shared/documents-files
- Android foreground service types (dataSync includes imports and local file processing): https://developer.android.com/develop/background-work/services/fgs/service-types
- Chaquopy compatibility table: https://chaquo.com/chaquopy/doc/current/versions.html
- Local source: android/prepare-assets.ps1, android/deploy-assets.ps1, tools/asset_pipeline/install.py, asset_exports.py, map_writer.py and park_export.py.
