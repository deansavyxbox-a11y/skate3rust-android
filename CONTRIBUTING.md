# Contributing

Issues and pull requests are welcome. Start with the Android setup/build guide in docs/ANDROID_PORT.md. Keep changes focused and describe the problem, implementation, and checks performed.

For a bug report, include device model, Android version, APK version, map and spawn, controller/touch mode, reproduction steps and a short relevant log excerpt. Redact account names, device serial numbers and local paths. Never attach proprietary game assets, full game archives, credentials or signing keys.

Use procedural fixtures for tests. Run the relevant Android Java/Rust tests and Python converter tests for the areas changed. Existing unrelated failures should be identified separately; do not claim the entire test suite passes when only targeted checks ran.

Please preserve upstream attribution and dependency licenses. Submit code and original test data that you have the right to contribute. Do not submit game data.
