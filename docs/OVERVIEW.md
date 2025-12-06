# Digi-Mobile overview

_Status: Experimental / Early._ Digi-Mobile is a truncated/pruned DigiByte node for Android that keeps consensus untouched while packaging build scripts, configs, and JNI glue for mobile use.

## Who this is for
- Android developers and power users curious about running DigiByte Core on devices.
- Contributors who want to harden scripts/configs without altering consensus logic.

## What you should have before starting
- Basic command line comfort and git installed.
- Android NDK + CMake for native builds (see [`CORE-SETUP`](CORE-SETUP.md)).
- adb from Android platform-tools for device workflows.

## Docs Index

See [`docs/README.md`](README.md) for a complete, organized index of all documentation.

Quick links:
- **Getting started:** [`GETTING-STARTED-DEV.md`](GETTING-STARTED-DEV.md) (developers) or [`GETTING-STARTED-NONTECH.md`](GETTING-STARTED-NONTECH.md) (non-technical users)
- **Installation:** [`MOBILE-INSTALL.md`](MOBILE-INSTALL.md) or [`RUNNING-ON-DEVICE.md`](RUNNING-ON-DEVICE.md)
- **Build details:** [`ADVANCED-ANDROID-BUILD.md`](ADVANCED-ANDROID-BUILD.md)
- **Configuration:** [`CONFIGURATION.md`](CONFIGURATION.md)
- **Core version management:** [`CORE-VERSIONING.md`](CORE-VERSIONING.md)

## Key directories
- `core/`: DigiByte Core source as a vendored git checkout pinned by `scripts/setup-core.sh`.
- `android/`: Android build glue, patches, and packaging assets (CMake/NDK).
- `scripts/`: Helper scripts for fetching core sources, printing the repository layout, and building artifacts.
- `config/`: Configuration files and pinned version references.
- `docs/`: Project documentation for Android builds, setup steps, and repository orientation.

## Android Core Integration
- Digi-Mobile builds DigiByte Core (tag `v8.26.1`) for Android arm64 via `scripts/build-core-android.sh`.
- The resulting headless `digibyted` binary is staged into the APK as `assets/bin/digibyted-arm64` and extracted to app-private storage on first run.
- The JNI bridge spawns the daemon with app-managed config and data directories so the APK remains self-contained.

## Getting Started
- Prefer the one-command wizard first: see [`README.md`](../README.md#one-command-setup-beginner-friendly) and run `./setup.sh`.
- Run the helper script [`scripts/setup-core.sh`](../scripts/setup-core.sh) to ensure the `core/` tree is initialized, on the expected tag, and pointed at the official DigiByte Core remote. See [`docs/CORE-SETUP.md`](./CORE-SETUP.md) for details and overrides.
- Review [`CONFIGURATION.md`](CONFIGURATION.md) to pick a pruned profile before pushing to devices.

## What could go wrong?
- Build failures if NDK/CMake versions are mismatched or the DigiByte Core checkout is missing.
- adb cannot find a device; rerun `adb devices` and ensure USB debugging/emulator is active.

## How to recover
- Re-run `./scripts/setup-core.sh` after pulling updates to refresh the DigiByte Core checkout and verify the remote/ref.
- Clean the Android build directory (`rm -rf android/build`) and rebuild if CMake cache issues appear.
- Unplug/restart the device or emulator and retry adb-based scripts.
