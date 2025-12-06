# Getting Started – Developers

This guide is for developers who want to **build and run Digi-Mobile from source** on macOS, Linux, or in a dev container.

## Prerequisites

Before you start, ensure you have:

- **Git** with network access to GitHub
- **JDK 17** (temurin recommended) – verify with `java -version`
- **Android SDK Platform 29** and **build-tools 29.0.3**
- **Android NDK r25.1.8937393** – set `ANDROID_NDK_HOME` or `ANDROID_NDK_ROOT`
- **CMake** (3.22+) – usually installed with Android SDK
- **Ninja build system** – `brew install ninja` (macOS) or `apt-get install ninja-build` (Linux)
- **Standard build tools**: bash, make, autoconf, pkg-config, curl

### Quick verification
```bash
java -version                    # Should show Java 17
which adb                        # Android platform-tools
echo $ANDROID_NDK_HOME          # Should be set
```

### Environment setup (example for Linux)
```bash
export ANDROID_HOME=/path/to/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/25.1.8937393
export PATH=$ANDROID_HOME/platform-tools:$PATH
```

## Quick Start – One Command

From the repository root, run:

```bash
./setup.sh
```

This wizard will:
1. Auto-detect your Android device (or emulator) if plugged in via USB
2. Verify JDK 17, Android SDK, NDK, and CMake are installed
3. Initialize the DigiByte Core checkout into `core/` (if missing)
4. Build or use prebuilt daemon binaries
5. Assemble the APK and optionally push it to your device

**After setup.sh completes**, use:
```bash
./status.sh              # Check if node is running on device
./stop.sh               # Stop the node
```

See [`docs/GETTING-STARTED-NONTECH.md`](GETTING-STARTED-NONTECH.md) for what to expect after the node starts.

## Manual Build Steps (Advanced)

If you prefer to understand each step or encounter issues, run them individually:

### Step 1: Initialize DigiByte Core
```bash
./scripts/setup-core.sh
```
- Clones or validates the DigiByte Core repository at tag `v8.26.1` into `core/`
- By default uses `https://github.com/DigiByte-Core/digibyte.git`
- **Override**: `CORE_REMOTE_URL=... CORE_REF=... ./scripts/setup-core.sh`

See [`docs/CORE-SETUP.md`](CORE-SETUP.md) for details.

### Step 2: Prepare Gradle Wrapper JAR
```bash
./scripts/prepare-gradle-wrapper.sh
```
- Decodes the wrapper JAR from base64 (binary not committed to avoid large diffs)

### Step 3: Cross-Compile DigiByte Daemon for Android
```bash
./scripts/build-android.sh
```
- Configures and builds DigiByte Core for **arm64-v8a** (64-bit ARM)
- Stages the daemon binary into `android/app/src/main/assets/bin/digibyted-arm64`
- Also stages `digibyted` into `android/app/src/main/jniLibs/arm64-v8a/` for JNI bridge use

Output files to verify:
```bash
file android/app/src/main/assets/bin/digibyted-arm64
file android/app/src/main/jniLibs/arm64-v8a/digibyted
```

Both should report: `ELF 64-bit LSB executable, ARM aarch64` with interpreter `/system/bin/linker64`.

See [`docs/ADVANCED-ANDROID-BUILD.md`](ADVANCED-ANDROID-BUILD.md) for build customization.

### Step 4: Assemble the APK
```bash
./gradlew assembleDebug
```
- Builds the Android app and bundles the staged daemon
- Output: `android/app/build/outputs/apk/debug/app-debug.apk`

Optional: assemble a release build:
```bash
./gradlew assembleRelease
```

### Step 5 (Optional): Push to Device and Run
```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.digimobile.app/.MainActivity
```

Or use the helper script:
```bash
./scripts/push-node.sh
./scripts/run-node.sh
./scripts/stop-node.sh
```

## Troubleshooting

### "Missing digibyted asset"
- Ensure `./scripts/build-android.sh` completed successfully
- Verify: `ls android/app/src/main/assets/bin/digibyted-arm64`
- If missing, rerun build-android.sh

### Gradle build fails with "NDK not found"
- Verify `$ANDROID_NDK_HOME` is set and points to NDK r25.1.8937393
- Check: `ls $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/`
- Update `.versions/android.env.sh` if using a different NDK version

### CMake or Ninja errors
- Ensure CMake 3.22+ is installed: `cmake --version`
- Install Ninja: `brew install ninja` (macOS) or `apt-get install ninja-build` (Linux)

### adb cannot find device
- Enable USB Debugging on your Android device
- Connect via USB and verify: `adb devices`
- If "unauthorized", tap "Allow" on your device's permission dialog

### Network timeout during core/ fetch
- Retry `./scripts/setup-core.sh`
- Check internet connection and GitHub access
- If behind a proxy, configure git: `git config --global http.proxy [proxy-url]`

### Linker errors: "incompatible with aarch64linux"
- **Cause:** Stale build cache or object files from previous build with different NDK/toolchain
- **Fix:** Clean and rebuild from scratch:
  ```bash
  ./scripts/clean-build.sh
  ./scripts/build-android.sh
  ```
- If that doesn't work, also clean the core dependencies:
  ```bash
  rm -rf core/depends/work core/build-android-arm64
  ./scripts/build-android.sh
  ```

## Next Steps

- **Configuration**: Read [`docs/CONFIGURATION.md`](CONFIGURATION.md) to tune pruning and memory settings before deployment
- **Security**: Review [`docs/SECURITY-PRIVACY.md`](SECURITY-PRIVACY.md) – hobby use only, do not store large amounts
- **Deployment**: See [`docs/RUNNING-ON-DEVICE.md`](RUNNING-ON-DEVICE.md) for persistent setups and monitoring
- **CI/CD**: Interested in GitHub Actions builds? See [`docs/CI-CD.md`](CI-CD.md) (coming soon)

## Related Documentation

- **Technical overview**: [`docs/OVERVIEW.md`](OVERVIEW.md)
- **Non-technical guide**: [`docs/GETTING-STARTED-NONTECH.md`](GETTING-STARTED-NONTECH.md) – for end users
- **Core setup details**: [`docs/CORE-SETUP.md`](CORE-SETUP.md)
- **Android build internals**: [`docs/ADVANCED-ANDROID-BUILD.md`](ADVANCED-ANDROID-BUILD.md)
- **APK installation**: [`docs/MOBILE-INSTALL.md`](MOBILE-INSTALL.md)
