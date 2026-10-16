# Android 0.2.0-rc1 release candidate plan

> Execute natively in this session using executing-plans and verification-before-completion.

**Goal:** Prepare a reviewable APK and release documentation without publishing.
**Architecture:** Keep the tested importer/gameplay implementation, package identifier, signer, API 26 floor, target 35, ARM64 ABI and Vulkan manifest requirement. Change only version metadata and documentation during candidate preparation.
**Spec:** User's compatibility-audit milestone and approved release-candidate preparation in this conversation.

## Tasks

- [x] Merge the historical public-APK audit and troubleshooting instructions into this feature checkout. Document both on-phone import routes and retained PC setup.
- [x] Set versionCode 2 and versionName 0.2.0-rc1; retain the existing development signer because updates and PC run-as setup depend on it.
- [x] Build optimized Rust/debug Android packaging and run Java/Python importer tests. Copy the exact APK into a private output directory; record checksum, signer, ABI/SDK/Vulkan, ZIP integrity, ELF alignment and dependencies including embedded Python wheels.
- [x] Back up current phone preferences/settings/asset marker. Install candidate as an update, reinstall the same artifact, compare saved files and installed APK bytes, and check startup/runtime. Never uninstall or clear data. Fresh installation requires a separate device; record it as untested if none is available.
- [x] Prepare candidate notes and a factual validation report. Review diff for private assets/credentials. Leave the candidate unpublished.

## Review focus

- Existing customized touch layout and active/previous asset generations survive updates.
- A version increase does not imply an update from the exact old public binary was tested.
- Embedded Python native dependencies may differ in page-size support from the gameplay library.
- Installation success does not establish Vulkan driver/gameplay compatibility on other devices.
- No proprietary assets, private logs/settings or signing keys belong in release files.

## Execution ledger

- Reusing the dirty importer feature checkout to preserve the already tested implementation. No branch reset or cleanup.
- Native execution and preparation are already authorized; no additional plan approval is needed. Publication remains outside this task.
- Review found interrupted ISO jobs left multi-GB staging directories. Added exact prior-job reclamation after journal recovery, plus a separate durable prior-job identifier surviving errors/cancellation. Two new tests red (missing method) then green. Follow-up review: no remaining important findings in fix.
- Full native/Android build passed; final Java 27/27 and Python 10/10, PowerShell package fixture pass. Exact public baseline upgrade and candidate reinstall both Success; installed APK checksum matches; nine captured files unchanged even after gameplay startup.
- 99 native ELFs inspected: FreeType has 4 KB LOAD alignment; 16 KB support not cleared. DownTownSkatePark startup shows a close/clipped camera view; recorded for focused follow-up. Neither gap was hidden by changing requirements or gameplay code.
- Candidate preparation complete; remaining publication gates are explicitly recorded in android/RELEASE_CANDIDATE_0.2.0_RC1.md. No public release or push.
