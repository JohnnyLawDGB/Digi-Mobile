# Android Core Build

This guide explains how Digi-Mobile builds DigiByte Core's headless daemon for Android and packages it into the APK. It targets **arm64-v8a** only and focuses on the daemon (no GUI, tests, or benchmarks).

## Prerequisites
- Android NDK installed and exported via `ANDROID_NDK_HOME` (or `ANDROID_NDK_ROOT` / `ANDROID_NDK`).
- Standard build tools (bash, make, autoconf) available on your host.
- DigiByte Core checkout present under `core/` (handled by `setup-core.sh`).

## Build Steps

1. **Initialize DigiByte Core**
   ```bash
   ./scripts/setup-core.sh
   ```
   This checks out DigiByte Core (tag v8.26.1 by default) into `core/` and wires the upstream remote.

2. **Cross-compile DigiByte Core for Android**
   ```bash
   ./scripts/build-core-android.sh
   ```
   - Cross-compiles the daemon for `arm64-v8a` using the NDK toolchain.
   - Builds only the headless `digibyted` binary.
   - Places the binary at `./build-android/bin/arm64-v8a/digibyted`.

3. **Stage the binary into the APK assets**
   ```bash
   ./scripts/build-android.sh
   ```
   - Invokes `build-core-android.sh` to make sure the daemon is up to date.
   - Copies the resulting binary into `android/app/src/main/assets/bin/digibyted-arm64` so Gradle can bundle it into the APK. If
     you skip this step but already have a freshly built daemon under `build-android/bin/arm64-v8a/` (or
     `android/build/android-prefix/arm64-v8a/bin/`), Gradle will now copy that binary into the assets directory automatically
     during `preBuild` so release builds still succeed.

4. **Assemble the Android app**
   ```bash
   cd android
   # Force the arm64 build on emulators that default to x86/x86_64:
   ANDROID_ABI=arm64-v8a ./gradlew assembleDebug
   ```
   The resulting APK contains the `digibyted` asset, which the app extracts to private storage on first run.
   Release builds will fail early if `android/app/src/main/assets/bin/digibyted-arm64` is missing; rerun
   `./scripts/build-android.sh` to stage the binary before packaging. When iterating on UI-only changes (e.g., `assembleDebug`),
   asset verification is skipped by default. You can force it back on with `-Pdigibyted.verifyAsset=true` or bypass it explicitly
   with `-Pdigibyted.verifyAsset=false`.

## Runtime behavior
- On launch, the JNI bridge copies the `digibyted-arm64` asset into `<filesDir>/bin/digibyted` and marks it executable.
- The `NodeService` ensures configs and data directories exist, then starts the daemon with the appropriate `-conf` and `-datadir` arguments.
- Currently only `arm64-v8a` is produced; extend the scripts if additional ABIs are needed.

