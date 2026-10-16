# Direct ISO import validation — 2026-10-15

## Scope and current status

Device: RedMagic 10 Pro / NX789J, Android 16, ARM64. Direct ISO conversion and publication passed on this device; University/MegaPark rendering and two cold launches passed after the focus-handoff correction described below. No public release has been created. Vulkan, SDK requirements, package identifier and signing certificate are unchanged.

## Source

Selected the user's local Xbox 360 ISO via Android's external-storage document provider. The Downloads provider view did not expose the file; browsing NX789J → Download did. The selected 7,838,695,424-byte ISO has SHA-256 `bd8d430188aa61b0ebf2e33e5672822dd7e59c9080fc09e802195e1ee75ebff0` on both PC and phone. No game data is downloaded or published by this workflow.

The native reader recognized the disc layout and extracted 6,404,940,920 bytes. All ten expected `worldDIST_*.big` district archives are present. The phone had about 38 GB free before the test with the source ISO already present. Space requirements vary with the image and conversion output; a private ISO copy, extracted disc, conversion intermediates and unpublished installation coexist during processing.

## Issues found and repaired

- **ISO selection:** the setup screen disabled Continue for ISO sources. Enabled the existing ISO service path for this development test. ISO browsing now permits provider MIME variations while the service still checks the filename and native disc format.
- **Python package layout:** a test using only the staged bundle in an isolated interpreter reproduced `ModuleNotFoundError: tools`. Preserve the `tools` package hierarchy and expose both package and legacy script import paths.
- **Validation order:** native validation requires `complete.json`; the ISO service called it before manifest creation. Create the integrity manifest before invoking native validation.
- **Full skate parks:** include the existing full-park exporter for MegaPark, IndustrialSkatePark, StartPark and DownTownSkatePark. It consumes the freshly converted collection database directly, without desktop installation-marker lookup. SHA-256 receipts and FNV bindings tie supplemental packages to their base maps; exporter failures abort the import.
- **Restart recovery:** a stopped worker's persisted CONVERTING state kept Play/Retry disabled. Distinguish an active worker from saved progress and expose retry for interrupted ISO and ZIP sources. Verified the stopped-job recovery UI on the phone.
- **Native decompression:** Android was using the pure-Python fallback because the fast backend searched for a Windows DLL only. Export the same existing bounds-checked RefPack decoder from `libskate3rust_import.so`, provide its Android native-library path to the Python wrapper, and require it to load in the Android bridge. The gameplay renderer is unchanged.
- Apply the existing progress-update throttle to native disc extraction callbacks as well.
- **Native startup handoff:** the initial Play test and a fresh-process retry triggered `Input dispatching timed out` for LaunchActivity's focus-loss event (8 seconds). Physics initialization took 10.4–11.6 seconds; the preserved previous generation also took 13.5 seconds under the test conditions. Release the loading window's focus and input target, let its focus-loss callback return, then start NativeActivity on the next UI turn. Two cold launches after this correction completed with physics still taking about 11.3 seconds and no new ANR. No physics or renderer code was changed; the underlying initialization duration remains a performance limitation.

## Verification

- 25 Java tests, zero failures/errors.
- Ten Python tests, zero failures/errors, including isolated generated-bundle startup, serial map orchestration/cancellation, full-park bindings/failure handling, exporter behavior and native RefPack comparison with the Python path plus malformed-input rejection.
- Full Android/Rust optimized build and APK assembly passed. Native import library exports `skate_refpack`; all its LOAD segments have 16 KB alignment.
- APK signing verified with the existing certificate fingerprint `82f0ecd863190a2e9224ad2be18455552a7c7804395d6949a80d4b701c7a4a81`.
- Data-preserving APK installation returned Success. Settings/touch controls were backed up locally before testing.
- Private ISO copy, native disc extraction, embedded Python startup, gameplay/skater/environment conversion, all ten district conversions and all four supplemental full parks completed on-device.
- Job `7a976b65-a318-4b54-84ce-f893aa8a0df9` reached COMPLETE. Final generation: 2,243 files / 1,143,422,520 bytes, with all ten maps and four catalogued park supplements plus receipts.
- Completion implies the service's native structural validation and publication integrity checks passed. No unvalidated generation was published.
- The previous generation's completion marker exactly matched the pre-import backup. Restore was exercised in both directions for startup comparison; the ISO generation is active at the end.
- Touch-controls preferences and graphics/default-map/character/gameplay settings matched their pre-import bytes after the import and again after map testing. The original University default selection was restored through the in-game map selector.
- University and MegaPark rendered with touch controls. Touch navigation successfully changed maps in both directions. Runtime reported simulation_failed=false. MegaPark's ramps and rails were visible; a full landing/grinding/controller regression was not performed.
- Temporary private ISO/extraction/conversion data was removed during normal successful publication. The phone returned to about 38 GB free; its original ISO remains in Downloads.

Installed final test APK SHA-256: `7d06bbf369af3f1677dbcf46c86a6569a23d8e0df7c502f1c2691e272d3642b7`.

Interrupted development passes were stopped before publication; only their verified job-owned pending directories were removed. The selected source ISO and active/previous asset generations were retained.

## Evidence and limitations

Local progress logs, settings backups and screenshots are held outside the repository under `outputs/iso-validation-2026-10-15`. Do not publish those private asset payloads. The stated acceptance checks are complete on this phone. Other phones, other ISO variants, sustained thermal performance, every map's interactive skating/grind/respawn behavior and a fresh physical-controller pass remain unverified. No universal Android compatibility or full gameplay parity is claimed. Native initialization can still take tens of seconds; the focus fix avoids the reproduced loading-window ANR rather than shortening physics preparation. The final handoff correction was verified with the completed ISO generation across two cold launches; the lengthy disc conversion was not repeated solely for this Java UI change.

Startup evidence: ANRs at 19:21:41 and 19:25:57 EDT before the focus correction. Corrected cold-launch runtime logs start at 19:30:41 and 19:32:06 EDT. Android's last-ANR timestamp remained 19:25:57 through both corrected launches and subsequent map transitions. Captured screenshots show the loading UI, University, MegaPark ramps and rails, and touch-driven map selection.

The APK tested in this report was a development build with version code 1 and the existing development certificate. Subsequent candidate preparation uses version code 2 / `0.2.0-rc1`; see [the candidate report](RELEASE_CANDIDATE_0.2.0_RC1.md). No APK or game assets were published during this validation.

### User follow-up

After this test, the user reported that the USB controller works and switching between several maps looks good with the ISO-imported assets. This adds a physical-controller and multi-map check on the same RedMagic; it does not establish exhaustive per-map landings, grinds or respawn coverage.
