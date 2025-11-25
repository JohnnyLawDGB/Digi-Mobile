# Digi-Mobile – Truncated DigiByte Core for Android

Digi-Mobile packages DigiByte Core as a pruned, Android-friendly full node build. The project keeps consensus and P2P behavior identical to upstream DigiByte Core while focusing on pruning-friendly defaults, cross-compiling, and Android packaging so devices can run a lightweight daemon and CLI.

## Status

**VERY EARLY, EXPERIMENTAL.** Expect rough edges while the Android build, pruning defaults, and packaging flow stabilize.

## Quickstart

1. Clone the repository:
   ```bash
   git clone https://github.com/your-org/Digi-Mobile.git
   cd Digi-Mobile
   ```
2. Initialize the vendored DigiByte Core tree and prerequisites:
   ```bash
   ./scripts/setup-core.sh
   ```
3. Build Android artifacts (defaults: `ARCH=arm64-v8a`, `API=29`):
   ```bash
   ./scripts/build-android.sh
   ```
4. Read more details in the documentation:
   - [`docs/OVERVIEW.md`](docs/OVERVIEW.md)
   - [`docs/CORE-SETUP.md`](docs/CORE-SETUP.md)
   - [`android/README-ANDROID-BUILD.md`](android/README-ANDROID-BUILD.md)
   - [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md)

## Design Principles

- Minimal changes to DigiByte Core: keep consensus and P2P logic untouched while pinning to a known release.
- Focus on pruning and Android packaging: prioritize storage-bounded node operation, cross-compiling, and deployable Android artifacts.

## Contributing

Issues and roadmap items live on the [issue tracker](https://github.com/your-org/Digi-Mobile/issues).

Good starter areas include:
- JNI bridge improvements to expose more DigiByte Core functionality to Android.
- Android app wrapper and UX around the packaged daemon/CLI.
- CI enhancements for reproducible Android builds and pruned configuration validation.
