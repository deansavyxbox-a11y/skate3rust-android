# On-phone Asset Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. The user previously selected native execution; preserve that method.

**Goal:** Let players install their own prepared asset ZIP or Xbox 360 ISO from Android's file picker, without ADB, while preserving working assets and preferences.

**Architecture:** Both sources produce a validated private staging directory consumed by one journaled installation store. A launcher setup gate prevents native gameplay from starting during import; a foreground service owns progress and cancellation. Direct ISO import embeds the existing Python converters and an Android-compatible native disc reader, with renderer-free Rust validation.

**Tech Stack:** Existing Java 17/Android SDK 35/minSDK 26/ARM64 app, Rust, Gradle, PowerShell 7; embedded Python with Android NumPy/Pillow for ISO conversion. Use Android's Storage Access Framework and dataSync foreground service.

**Spec:** `docs/superpowers/specs/2026-10-13-android-asset-import-design.md` (read it before execution).

## Global Constraints

- Both import paths are required for complete feature acceptance; an archive-only checkpoint is explicitly intermediate.
- Original ISOs, extracted files, generated maps, signing keys and private device data never enter Git or public release assets.
- Never uninstall or clear the user's current app data for a fresh-install test.
- Preserve files/installation, current settings and touch SharedPreferences; support existing ADB installations.
- Import is permitted only before the native game has initialized in the current process. No live replacement of Bevy assets.
- Android source inputs are content URIs, never guessed external filesystem paths; original files are never modified/deleted.
- Use bounded extraction, verified payload hashes and durable recovery; do not publish an incomplete generation.
- Initial conversion processes maps serially; do not invoke desktop executables, shell extraction, remote conversion or Blender.
- No automatic GitHub release until both modes pass acceptance; preserve the current public APK.

## Review Focus

- Providers reporting unknown size or disconnecting halfway through: fail without publishing or touching the old installation (Tasks 2/4).
- Relaunch while the native game is still alive: bring forward gameplay and refuse import, including external intents (Task 3).
- Saved map absent from the new package: preserve other settings, choose a valid installed fallback, explain it (Task 1).
- Android service timeout or process death during either rename: recover a coherent generation before Play (Tasks 1/4).
- A structurally valid Xbox ISO for another title or revision: validate Skate resources, report unsupported input, retain old gameplay (Tasks 6/8).

---

## File structure and shared contracts

Java package remains `android/app/src/main/java/com/skate3rust/android/`. New focused files:

- `ImportModel.java`: immutable job snapshots, phase enum, cancellation interface and typed failures.
- `InstallationStore.java`: locking, journal, validation/publication, rollback and recovery; pure file operations with injectable fault points.
- `AssetManifest.java`: bounded manifest/complete JSON parsing and runtime payload contract.
- `PreparedZipImporter.java`: bounded ZIP extraction and manifest verification.
- `SetupActivity.java`: chooser/status/Play/Manage assets UI; does not load the game library.
- `ImportService.java`: foreground job lifecycle, notification, cancellation and durable progress.
- `ImportSource.java`: provider URI/grants, streaming/seekability and storage preflight.
- `IsoConverter.java`: bridge to embedded conversion and native validation.
- Tests live under `android/app/src/test/java/com/skate3rust/android/` and Android instrumented tests under `android/app/src/androidTest/java/com/skate3rust/android/`.
- `tools/asset_pipeline/android_install.py` adapts converters; `tools/asset_pipeline/test_android_install.py` tests orchestration without game content.
- `crates/skate-import/` contains renderer-free validation and XDVDFS parsing, exposed to Android through a dedicated JNI library in `android/import-native/`.
- `android/package-assets.ps1` produces importable ZIPs from validated staging.

Shared interfaces (later tasks must retain names/types):

- `ImportModel.Phase`: IDLE, INSPECTING, IMPORTING, CONVERTING, VALIDATING, PUBLISHING, COMPLETE, CANCELLED, FAILED, INTERRUPTED.
- `ImportModel.Progress`: `void report(Phase phase, long completed, long total, String item)`; `total=-1` means unknown.
- `ImportModel.Cancellation`: `void check() throws InterruptedIOException`.
- `AssetManifest.read(File root)` returns a validated manifest with `entries`, `totalBytes`, `maps` and schema version; `verify(File root, Progress, Cancellation)` hashes payloads.
- `InstallationStore.recover()`, `newStaging(String jobId)`, `publish(File staging, Progress, Cancellation)`, `restorePrevious()`, `isPlayable()`; constructor takes `File filesRoot` and injectable file-operation fault hook for tests.
- `PreparedZipImporter.importFrom(InputStream source, File staging, Progress, Cancellation)` returns `AssetManifest` only after complete verification.
- `ImportSource.open(Uri uri)` returns a closeable input handle with stream, optional seekable descriptor and declared byte count (unknown is -1).
- `IsoConverter.convert(ImportSource.Handle source, File staging, File work, Progress, Cancellation)` returns `AssetManifest` after native validation.

### Task 1: Recoverable installation store and manifest

**Files:** Create ImportModel.java, AssetManifest.java, InstallationStore.java and corresponding JVM tests; modify android/app/build.gradle for JUnit 4.13.2 and a JVM JSON implementation; retain existing Java tests.

**Consumes:** Existing prepare-assets.ps1 manifests and files/installation layout.
**Produces:** Shared interfaces above, with explicit recover-before-Play semantics.

- [ ] Write tests `rejectsBadManifest`, `publishesValidatedStaging`, `preservesGraphicsAndTouch`, `fallsBackForMissingSavedMap`, `recoversEveryPublicationBoundary`, `restoresPrevious`. Assert malformed version/count/hash/path fails; old graphics bytes remain identical; touch preferences are never opened by the store; all simulated journal/rename failures retain one playable generation.
- [ ] Run `android/gradlew.bat -p android :app:testDebugUnitTest -x :app:buildRust` and confirm the new tests fail because the new contracts are absent.
- [ ] Implement the model, BOM-tolerant bounded JSON parsing, process/file lock, journal and rename recovery. Flush journal boundaries. Required runtime payloads derive from the existing GameAssets/core readers, not merely complete.json existence. Include startup adoption of validated legacy ADB installs. Use imported defaults only for first installation; preserve compatible live settings on replacement.
- [ ] Run the same JVM tests; require all new cases pass. Run `pwsh -File android/test-touch-controls.ps1` for preference/layout regression.
- [ ] Commit the independently tested store and model.

### Task 2: ZIP import and PC packaging

**Files:** Create PreparedZipImporter.java, ZIP metadata helper if needed, its JVM tests, android/package-assets.ps1; modify android/test-asset-validation.ps1 to exercise packaged fixtures.

**Consumes:** Task 1 manifest/staging contracts.
**Produces:** `PreparedZipImporter.importFrom(...)` and a ZIP64-capable source format compatible with existing staging outputs.

- [ ] Write generated ZIP tests `importsPreparedFixture`, `rejectsTraversalAndLinks`, `rejectsDuplicateAndUnlistedPayload`, `rejectsHashOrLengthMismatch`, `rejectsTruncatedOrOversizedInput`, `acceptsZip64`, `cancelLeavesStagingUnpublished`, `providerFailurePreservesInstallation`. Assert the source is never deleted and live installation bytes do not change.
- [ ] Run the targeted JVM test class and confirm failures before implementation.
- [ ] Implement extraction as regular files only with canonical containment, bounded names/counts/metadata/output bytes, incremental hashing and cancellation checks. Parse ZIP link attributes where present; never materialize archive links. Finish validation of all archive metadata before publication. If a stream cannot expose the metadata needed for strict link/duplicate validation, implement bounded metadata spooling instead of accepting unchecked entries; do not retain a second full compressed copy. Fail explicitly for unsupported TAR/Windows release ZIPs.
- [ ] Implement `package-assets.ps1 -Staging <path> -Output <zip>`: call existing Test-StagedAssets, require new output, preserve root manifest layout, use ZIP64 for large payloads, and never touch input assets. Test with generated large-entry metadata and small real bytes rather than publishing private assets.
- [ ] Run JVM/archive validation tests and a package→import fixture round-trip. Require exact manifest payload hashes. Commit.

### Task 3: Launcher setup gate and source selection

**Files:** Create SetupActivity.java and ImportSource.java; modify LaunchActivity.java and AndroidManifest.xml; add setup instrumentation tests.

**Consumes:** Store recovery/isPlayable and source-handle contracts.
**Produces:** A setup screen reachable before native initialization, native-session guard, and persisted URI grants when supported.

- [ ] Write instrumentation tests `freshLaunchShowsTwoSources`, `installedLaunchShowsPlayAndManage`, `cancelPickerKeepsInstallation`, `unknownSizeUriIsSupported`, `relaunchReturnsToExistingGame`, `externalGameLaunchIsDenied`. Assert no native startup during setup and no import UI when gameplay owns the session.
- [ ] Run tests on an isolated test application/data directory; observe expected preimplementation failures without clearing the user's main app.
- [ ] Implement recovery-first routing and two source choices using ACTION_OPEN_DOCUMENT; preserve current branding for Play→LaunchActivity→GameActivity. Make GameActivity non-exported. Use one explicit native-session guard shared with launch routing; never use a manifest-presence check as the only validity decision. URI permissions are released when no longer needed.
- [ ] Implement touch/controller focus, readable insufficient-space/provider errors, Play/Manage/Restore navigation and the required close/reopen-session message after gameplay initialization.
- [ ] Run instrumentation and existing touch checks; verify the main app's preferences remain unchanged. Commit.

### Task 4: Foreground import service and archive end-to-end

**Files:** Create ImportService.java and focused notification/journal helpers; wire SetupActivity; modify AndroidManifest.xml for foreground/dataSync permissions, notification permission and non-exported service; add service tests.

**Consumes:** Tasks 1–3; store lock, progress/cancellation, URI handle, ZIP importer.
**Produces:** Persistent job status and first usable on-phone archive import.

- [ ] Write `onlyOneImportRuns`, `rotationReattachesProgress`, `cancelStopsBeforePublish`, `timeoutStopsSafely`, `processDeathReportsInterrupted`, `deniedNotificationPermissionDoesNotBreakImport`, `noPlayDuringPublish`. Assert terminal paths close descriptors and stop foreground work; a timeout in publication recovers before Play.
- [ ] Run targeted tests and confirm failures before implementation.
- [ ] Implement one user-started foreground job with phase/item/byte snapshots, ongoing progress notification, cancellation and onTimeout handling. Persist job metadata, never source contents in logs. On restart report interrupted work and offer Retry; do not claim byte-level resume. Gate all installation mutations with the store lock. Measure expanded bytes against available storage throughout import.
- [ ] Test a private prepared archive on an isolated app ID on the RedMagic, then verify skating and full parks. Test corrupt input, cancellation and interruption with previous working generation preserved. Label any delivered archive-only APK an intermediate test build, not completion of both modes. Commit.

### Task 5: Renderer-free native validation

**Files:** Create crates/skate-import/Cargo.toml, src/lib.rs, src/validation.rs; create android/import-native/Cargo.toml and src/lib.rs; modify root Cargo.toml, android/build-rust.ps1, Android build configuration; add Rust fixture tests.

**Consumes:** Existing skate-data reader/validation primitives and Task 1 staging contract.
**Produces:** `validate_installation(root: &Path) -> Result<ValidationReport, ImportError>` without Bevy/Vulkan, plus JNI wrapper `nativeValidate(String root)` returning structured JSON or a typed Java error.

- [ ] Write native tests for absent core files, malformed SKATE geometry/rails, invalid supplemental receipt bindings and valid generated minimal packages; assert validation loads no renderer and malformed offsets fail without panic.
- [ ] Run `cargo test --locked -p skate-import` and confirm missing/new validation cases fail.
- [ ] Implement validation by sharing existing parsers; extract minimal non-renderer helpers only when required, preserving desktop callers. Package separate importer JNI library so setup does not initialize skate3rust_android.
- [ ] Run native tests and Android validation instrumentation on generated fixtures, then privately on the current prepared installation. Run park converter tests and relevant data regressions. Commit.

### Task 6: Bounded ISO filesystem reader

**Files:** Create crates/skate-import/src/xdvdfs.rs and generated filesystem tests; extend android/import-native JNI and IsoConverter.java input bridge.

**Consumes:** Seekable source descriptor or explicitly budgeted private source copy; job work directory.
**Produces:** `inspect_iso<R: Read + Seek>(reader: &mut R) -> Result<DiscIndex, ImportError>` and `extract_required<R: Read + Seek>(reader: &mut R, index: &DiscIndex, destination: &Path, observer: &dyn ImportObserver) -> Result<(), ImportError>`; `DiscIndex { partition_offset: u64, files: Vec<DiscEntry> }` and `DiscEntry { path: String, offset: u64, size: u64 }` live in xdvdfs.rs. Define `ImportObserver` in lib.rs with `fn report(&self, phase: &str, completed: u64, total: Option<u64>, item: &str)` and `fn cancelled(&self) -> bool`.

- [ ] Write synthetic-image tests `readsSupportedPartitionLayouts`, `rejectsMalformedDirectoryTree`, `rejectsExtentOverflowAndTraversal`, `rejectsOtherGame`, `cancellationStopsExtraction`. Include cycles, overlaps/out-of-image extents and unsupported source layout messages.
- [ ] Run targeted Rust tests; confirm missing parser behavior fails.
- [ ] Implement bounded XDVDFS inspection and selective required-resource extraction from an immutable descriptor. Handle raw/XGD partition locations using actual signatures and validated bounds rather than unrestricted scanning. Validate default.xex/title identity and required Skate resources. Non-seekable sources require a disclosed, space-checked private copy and chunked cancellation.
- [ ] Run parser tests, ARM64 cross-check/build and private extraction comparisons against the already owned source. Inspect only required resources; never commit those fixtures. Commit.

### Task 7: Embedded converter and first map proof

**Files:** Add embedded runtime Gradle configuration, IsoConverter.java, tools/asset_pipeline/android_install.py, test_android_install.py, an Android Python entry module and dependency/provenance notices.

**Consumes:** Task 6 extracted resource root, existing Python converter functions, Task 5 native validation.
**Produces:** `convert_owned(game_root, staging, work, observer, maps)` for Android and a complete privately validated University installation.

- [ ] Write Python adapter tests asserting serial conversion, no subprocess/network calls, correct cancellation propagation and optional-content reporting; mock converters to generate original fixture bytes. Run `python -m unittest tools.asset_pipeline.test_android_install` and confirm failure first.
- [ ] Package Chaquopy with ARM64 Python/NumPy/Pillow. Use the official compatibility table to pin an exact supported release/Python/package combination in the commit and document it. Check existing hashlib/file_digest and vendor syntax against the selected Python version before changing converters. Verify minimal NumPy/Pillow JNI smoke tests on the RedMagic before expanding the packaged source set.
- [ ] Refactor only desktop subprocess orchestration into callable conversion boundaries; retain desktop CLI behavior. Replace executable --check-assets calls with Task 5 validation. Limit packaged source to required original pipeline/vendor code and notices, with no private caches/data or executable Python from user archives.
- [ ] Convert core/skater/HUD/environment plus University from the private ISO entirely on phone. Record duration, peak private storage and memory observations; compare parsed outputs and deterministic hashes with PC preparation. Require playable University. If dependencies, image parsing or memory block this, report evidence and revise the approach before claiming ISO support.
- [ ] Run Python adapter, existing park export and desktop converter regressions; commit the proven converter adapter/runtime configuration.

### Task 8: All-map ISO setup and shared publication

**Files:** Extend android_install.py, IsoConverter.java, ImportService.java and SetupActivity.java; reuse map-start and park fingerprint/receipt rules through tested shared conversion logic; add orchestration tests.

**Consumes:** All earlier tasks; existing park_export export_full_park/map_writer.
**Produces:** Full ISO path generating Task 1 manifests and publishing through InstallationStore.

- [ ] Write tests `convertsAllSupportedMapsSerially`, `includesFourFullParkLayouts`, `bindsSupplementReceipts`, `doesNotPublishMissingRequiredMap`, `cancelBetweenMapsPreservesOldInstall`, `unsupportedRevisionIsReported`, `doesNotReuseWrongSourceCheckpoint`.
- [ ] Run new tests; confirm missing wiring fails.
- [ ] Implement all discovered supported disc maps, four park supplements, consistent Android spawn handling, source identity/format checks and manifest generation. Release per-map caches/intermediates promptly. Report explicit optional content limitations; do not silently treat required park/map failure as full success.
- [ ] Route ISO output through native validation and exactly the same journaled store used by ZIP import. Exercise unknown provider size, foreground/background changes, cancellation and timeout during each phase. No converter state should overwrite existing settings.
- [ ] Run a full private ISO→all-map setup on the phone, then compare map/geometry/collision/rail metadata with PC outputs. Commit.

### Task 9: Acceptance, production packaging and public documentation

**Files:** Update README.md, docs/ANDROID_PORT.md, CONTRIBUTING.md, android/app/build.gradle and release notes; retain private measurements outside Git.

**Consumes:** Working archive and ISO modes from Tasks 4/8, unchanged signing identity stored only locally.
**Produces:** Versioned, non-debuggable candidate APK that installs assets without run-as and a documented supported source/device scope.

- [ ] Run generated archive/store/parser tests, Android source/service tests, converter/park tests and existing touch/layout/startup regressions. Record actual commands/results and separate unrelated upstream failures.
- [ ] On isolated test data, exercise first install, replacement, rollback, process death and insufficient storage for both paths. Check all ten maps, four visible full park layouts, controller/touch switching and settings retention. Confirm no private fixtures or credentials in staged source/APK.
- [ ] Measure APK size, ISO conversion duration, peak temporary storage and device memory behavior. Update instructions with measured limits; do not invent timing or device compatibility claims.
- [ ] Build an incremented versionCode/versionName APK, non-debuggable, signed locally with the current identity. Verify APK signature/update compatibility and archive/ISO import without run-as. Preserve the user's installed package/data unless a fully backed-up update is actually needed for acceptance.
- [ ] Perform one independent whole-branch code review required by native execution workflow; fix important findings and rerun affected checks. Review actual final diff against every spec requirement.
- [ ] Commit final source/docs. Publish only when both paths pass acceptance, with APK/checksums/source and explicit experimental limits. Verify public APK download hash. Leave prior release available and never upload game assets.

## Execution handoff and completion evidence

Execute natively in this chat, following the user's earlier native-execution preference. Each task records its failing/passing checks and commit. Track archive-only progress separately from overall completion. Required final evidence: public source commit, build/signature checks, test results, private on-device acceptance report without game content, measured resource limits and both user-visible paths working.

## Self-review result

The plan maps all spec sections to Tasks 1–9. Archive/ISO paths share one installation contract; every later interface is defined above or in its producing task. All five Review Focus cases have explicit owner tests. Dependency versions are resolved and pinned as part of the Android packaging proof in Task 7, rather than claiming untested wheel compatibility. No runtime feature work has been performed during planning.
