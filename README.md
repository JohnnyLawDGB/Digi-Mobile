# Digi-Mobile – Truncated DigiByte Core for Android

Digi-Mobile packages DigiByte Core as a pruned, Android-friendly full node build. The project keeps consensus and P2P behavior identical to upstream DigiByte Core while focusing on pruning-friendly defaults, cross-compiling, and Android packaging so devices can run a lightweight daemon and CLI.

## Plain-English Start Here
- Digi-Mobile is for **testing and hobby use** on spare Android devices to help the DigiByte network.
- Read the quick guide: [`docs/GETTING-STARTED-NONTECH.md`](docs/GETTING-STARTED-NONTECH.md) for a non-technical overview and examples of good vs. bad uses.
- **Disclaimer:** Experimental software with no guarantees; do not use for serious amounts of money.

## Install on Android via APK (no building)
- On your Android device, open this GitHub repo in a browser, tap **Releases**, and download the latest APK.
- Install the APK (enable "unknown sources" if prompted), then open Digi-Mobile and tap **Set up and start node**.
- **Hobby/testing only.** See [`docs/MOBILE-INSTALL.md`](docs/MOBILE-INSTALL.md) for the step-by-step walkthrough.

## One-Command Setup (Beginner-Friendly)
- **Hobby/testing only. Not for serious funds.**
- Plug in an Android device with USB debugging enabled and run:
  ```bash
  ./setup.sh
  ```
- The wizard auto-detects your device, checks the Android NDK, initializes the `core/` DigiByte Core checkout if missing (and fixes its remote if needed), and either builds or uses prebuilt binaries.
- After it finishes, use `./status.sh` to check the node and see [`docs/GETTING-STARTED-NONTECH.md`](docs/GETTING-STARTED-NONTECH.md) for what to expect.

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
   - Optional: override the upstream with `CORE_REMOTE_URL=https://github.com/DigiByte-Core/digibyte.git` (default) or pin a different ref with `CORE_REF=develop`.
3. Re-create the Gradle wrapper JAR (kept as base64 text to avoid binary commits):
   ```bash
   ./scripts/prepare-gradle-wrapper.sh
   ```
4. Build Android artifacts (defaults: `ARCH=arm64-v8a`, `API=29`):
   ```bash
   ./scripts/build-android.sh
   ```
4. Read more details in the documentation:
   - [`docs/OVERVIEW.md`](docs/OVERVIEW.md)
   - [`docs/CORE-SETUP.md`](docs/CORE-SETUP.md)
   - [`android/README-ANDROID-BUILD.md`](android/README-ANDROID-BUILD.md)
   - [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md)

Digi-Mobile currently wraps DigiByte Core v8.26.x (default tag `v8.26.1`). See [`docs/CORE-VERSIONING.md`](docs/CORE-VERSIONING.md) for details.

## Design Principles

- Minimal changes to DigiByte Core: keep consensus and P2P logic untouched while pinning to a known release.
- Focus on pruning and Android packaging: prioritize storage-bounded node operation, cross-compiling, and deployable Android artifacts.

## Code Quality & Hardening

Scripts and docs aim to be self-explanatory with clear headers, environment notes, and conservative defaults for small devices. Contributions that improve clarity, comments, and safety checks are welcome—see [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md).

## Security & Privacy
- Hobby/testing only: do not store large or irreplaceable DigiByte balances here.
- Consumer Android devices can be lost, stolen, or compromised—protect the device and keep separate backups of any keys.
- See [`docs/SECURITY-PRIVACY.md`](docs/SECURITY-PRIVACY.md) for full guidance and risk notes.

## Contributing

Issues and roadmap items live on the [issue tracker](https://github.com/your-org/Digi-Mobile/issues). See [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md) for expectations and style notes.

Good starter areas include:
- JNI bridge improvements to expose more DigiByte Core functionality to Android.
- Android app wrapper and UX around the packaged daemon/CLI.
- CI enhancements for reproducible Android builds and pruned configuration validation.
