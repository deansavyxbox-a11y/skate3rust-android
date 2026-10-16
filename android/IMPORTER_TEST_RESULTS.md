# Prepared ZIP importer validation — 2026-10-15

Device: RedMagic 10 Pro / NX789J, Android 16, ARM64. This is development-build evidence, not a public release or broad device-compatibility claim.

## Failure and fix

The previous phone job failed after extraction with `Prepared ZIP central directory is missing or too large`. The selected ZIP matched the PC checksum. `ZipInputStream.getNextEntry()` stops at the central directory rather than reading the source to EOF. Small archives happened to have their end records inside the read-ahead buffer; larger or fragmented provider streams did not.

Added regression cases for a document provider returning 37-byte reads and an end record beyond read-ahead (20 KB ZIP comment). Both failed with the original reader. The reader now drains remaining source metadata into its bounded tail before validating the central directory. The drain remains cancellable and bounded to 8 MB. Existing integrity, path, symlink, ZIP64 and publication checks remain enabled.

## Verification

- Android Java unit suite: 24 tests, zero failures/errors; both new regression cases pass.
- `assembleDebug` succeeded. Reused the existing native libraries because this fix only changes Java ZIP reading and tests.
- APK signature verified and matched the existing development certificate. `adb install -r` returned `Success`.
- Exact private test ZIP: 951,210,388 bytes, SHA-256 `e5d22a14df9f3be05e9a92a39570924867afe3feee94f8b44f6ccaaadf289655`; phone copy matched.
- Selected the ZIP through Android's document picker. Foreground import reached `COMPLETE` / `Assets installed. You can play now.`
- Published installation reports ten maps, 2,249 files and 1,177,674,153 bytes after carrying forward settings.
- `installation.previous/complete.json` exists. No uninstall or clear-data command was used.
- Compared touch-controls preferences plus default-map, graphics, character and gameplay settings against the pre-test backup: all five files unchanged byte-for-byte.
- Pressed Play. Gameplay rendered the environment and skater with touch controls. Runtime reported Running, CHARACTER_READY and simulation_failed=false. A stationary startup sample was around 100 FPS; no sustained performance claim.

Installed test APK SHA-256: `2623949d896a125da034dfe32c476aa322c6871c021eb344735026704a41f28a`.

## Remaining validation

Prepared ZIP acceptance is confirmed on this phone. Direct ISO conversion was initially disabled during this ZIP test; subsequent full-disc results are recorded in [ISO import validation](ISO_IMPORT_VALIDATION.md). Gameplay across every map, interactive controller regression, clean installation on another device, and public release packaging remain separate work. The app still uses development version metadata; this build has not been published.

Private asset ZIPs, APK backups, settings backups, logs and screenshots are held in local output directories outside the repository and must not be uploaded as game-asset distributions.
