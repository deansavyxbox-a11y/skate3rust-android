# ISO import progress follow-up — 2026-10-16

This is a post-`0.2.0-rc2` source and local-device change. The public `0.2.0-rc2` APK does **not** include it.

## Reproduced behavior and cause

During a real RedMagic 10 Pro ISO import, the public APK's persisted job stayed in `CONVERTING` with `completed=0` and `total=-1` while its `item` advanced from University to later maps. The setup screen always formatted `completed` as MB, yielding `0.0 MB`, and its preference listener ignored changes to `item`. A screenshot still showed the University extraction step after the worker had progressed to DownTown. The import subsequently completed. Thus the stale display was a UI reporting bug, not evidence that this observed conversion had stopped.

## Change and validation

- The screen refreshes when the current item, map context or measured total changes.
- ISO copy and disc extraction continue to show measured bytes. Conversion and other unknown-total phases no longer display `0.0 MB`.
- The converter's existing `Converting map X of Y` event remains visible alongside later per-map steps; no artificial percentage or time estimate is shown.
- The Java progress-format tests failed before the implementation because the formatter did not exist, then passed. The complete Android unit-test task and a local debug APK assembly passed. The build reused unchanged native libraries; it did not modify conversion output, the manifest requirements or signing configuration.
- The local APK installed over the RedMagic app with ADB `Success`. The active installation's `complete.json` and `settings/default-map.json` SHA-256 values matched before and after the reinstall. During a second ISO run, screenshots showed measured copy progress, a conversion step without the false MB counter, and the visible map count advancing from `1 of 10: University` to `3 of 10: DownTown` without reopening the screen.

The second import reached `COMPLETE` with `Assets installed. You can play now.` The active University default remained selected; `installation.previous` was retained, no pending import directory remained, and internal free space returned to about 42 GB. A launch screenshot showed University, the skater and the touch overlay rendering. Interactive skating and controller input were not retested during this progress-focused check. Screenshots and the user's proprietary disc/assets are retained only in local private evidence and are not committed. No new public APK was published; the next APK must use new version metadata and receive release-package and update validation before publication.
