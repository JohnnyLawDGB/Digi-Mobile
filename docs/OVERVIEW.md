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
- [`GETTING-STARTED-NONTECH.md`](./GETTING-STARTED-NONTECH.md): Plain-English guide for non-technical helpers and users.
- [`CORE-SETUP.md`](./CORE-SETUP.md): Initialize the DigiByte Core source tree and prerequisites.
- [`CONFIGURATION.md`](./CONFIGURATION.md): Runtime settings tuned for pruned mobile nodes.
- [`ANDROID-BUILD.md`](./ANDROID-BUILD.md): Android build flow and architecture notes.
- [`android/README-ANDROID-BUILD.md`](../android/README-ANDROID-BUILD.md): Android-side toolchain specifics and Gradle/NDK tips.
- [`ANDROID-INSTALL.md`](./ANDROID-INSTALL.md): Steps to push binaries and run on devices or Termux.
- [`PRUNING.md`](./PRUNING.md): Details on pruned node behavior and storage targets.
- [`RUNNING-ON-DEVICE.md`](./RUNNING-ON-DEVICE.md): adb-based push/run/stop helpers.
- [`JNI-BRIDGE.md`](./JNI-BRIDGE.md): Notes on the experimental JNI layer.

## Key directories
- `core/`: DigiByte Core source tracked as a submodule pinned to the version in `.versions/core-version.txt`.
- `android/`: Android build glue, patches, and packaging assets (CMake/NDK).
- `scripts/`: Helper scripts for fetching core sources, printing the repository layout, and building artifacts.
- `config/`: Configuration files and pinned version references.
- `docs/`: Project documentation for Android builds, setup steps, and repository orientation.

## Getting Started
- Initialize the DigiByte Core submodule following [`docs/CORE-SETUP.md`](./CORE-SETUP.md).
- Run the helper script [`scripts/setup-core.sh`](../scripts/setup-core.sh) to ensure the `core/` tree is initialized and on the expected tag.
- Review [`CONFIGURATION.md`](CONFIGURATION.md) to pick a pruned profile before pushing to devices.

## What could go wrong?
- Build failures if NDK/CMake versions are mismatched or the submodule is uninitialized.
- adb cannot find a device; rerun `adb devices` and ensure USB debugging/emulator is active.

## How to recover
- Re-run `./scripts/setup-core.sh` after pulling updates to sync the submodule.
- Clean the Android build directory (`rm -rf android/build`) and rebuild if CMake cache issues appear.
- Unplug/restart the device or emulator and retry adb-based scripts.
