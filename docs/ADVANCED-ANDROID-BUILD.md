# Advanced Android Build – Reference & Customization

This document is for developers who need to customize the Android build, use different NDK versions, or understand the build internals.

**For most use cases**, see [`docs/GETTING-STARTED-DEV.md`](GETTING-STARTED-DEV.md). This guide is optional.

## Overview

Digi-Mobile builds DigiByte Core for Android in two main steps:

1. **Cross-compile DigiByte daemon** (C++) via NDK and CMake
2. **Stage binaries** into Android app assets for packaging
3. **Assemble APK** with Gradle, bundling the daemon and JNI bridge

## Prerequisites

- **Android NDK r25.1.8937393** (or configurable in `.versions/android.env.sh`)
- **CMake 3.22+**
- **Ninja build system**
- **JDK 17**
- **Standard build tools** (autoconf, pkg-config, python3)
- DigiByte Core source initialized via `./scripts/setup-core.sh`

## Build Configuration

Edit or override these environment variables:

### `.versions/android.env.sh` (file)
Pinned versions for JDK, NDK, build-tools, CMake, and API levels:
```bash
export ANDROID_NDK_API_LEVEL=29
export ANDROID_NDK_VERSION="25.1.8937393"
export ANDROID_NDK_HOME="/path/to/ndk-r25"
```

### Runtime overrides (environment)
```bash
ANDROID_NDK_HOME=/custom/ndk \
  ANDROID_NDK_API_LEVEL=30 \
  ./scripts/build-android.sh
```

## Build Targets & Architectures

Currently supported:

| ABI | Description | Status |
|-----|-------------|--------|
| `arm64-v8a` | 64-bit ARM (aarch64) | ✅ Primary (tested, CI default) |
| `armeabi-v7a` | 32-bit ARM (armv7a) | ⚠️ Supported but not tested in CI |

The staging script only produces `digibyted-arm64` and `digibyte-cli-arm64` assets for the APK. To add other ABIs, edit `scripts/build-android.sh`.

## Build Scripts Explained

### `./scripts/setup-core.sh`
Initializes or validates the DigiByte Core checkout.

**What it does:**
- Creates/validates `core/` git repository
- Fixes remote URL if needed (ensures it points to official DigiByte Core)
- Fetches tags and checks out the pinned ref (default: `v8.26.1`)

**Usage:**
```bash
./scripts/setup-core.sh                    # Default: core/, v8.26.1
CORE_DIR=/tmp/core CORE_REF=develop ./scripts/setup-core.sh
```

See [`docs/CORE-SETUP.md`](CORE-SETUP.md) for details.

### `./scripts/build-android.sh`
Main orchestration script for cross-compiling and staging.

**What it does:**
1. Sources `.versions/android.env.sh` and NDK environment
2. For each ABI (default: `arm64-v8a`):
   - Runs CMake with Android toolchain
   - Builds `digibyted` and `libdigimobile_jni.so`
   - Stages binaries into:
     - `android/app/src/main/jniLibs/arm64-v8a/` (for JNI bridge)
     - `android/app/src/main/assets/bin/digibyted-arm64` (for app extraction)
3. Prints progress messages to stdout

**Usage:**
```bash
./scripts/build-android.sh                 # Default: arm64-v8a, API 29
ABI=armeabi-v7a ./scripts/build-android.sh # Build 32-bit variant
```

**Output files to verify:**
```bash
file android/app/src/main/assets/bin/digibyted-arm64
# Expected: ELF 64-bit LSB executable, ARM aarch64, version 1 (SYSV), dynamically linked, interpreter /system/bin/linker64, ...
```

### `./scripts/prepare-gradle-wrapper.sh`
Decodes the Gradle wrapper JAR from base64 (to avoid committing binaries).

**Usage:**
```bash
./scripts/prepare-gradle-wrapper.sh
```

## Gradle Build

Once binaries are staged, Gradle assembles the APK:

### Debug Build
```bash
./gradlew assembleDebug
```
Output: `android/app/build/outputs/apk/debug/app-debug.apk`

**Asset verification:** Skipped for debug builds by default (faster iteration).  
Force verification: `./gradlew assembleDebug -Pdigibyted.verifyAsset=true`  
Skip verification: `./gradlew assembleDebug -Pdigibyted.verifyAsset=false`

### Release Build
```bash
./gradlew assembleRelease
```
Output: `android/app/build/outputs/apk/release/app-release.apk`

**Asset verification:** Mandatory for release builds. The build fails early if `android/app/src/main/assets/bin/digibyted-arm64` is missing.

## Runtime Behavior

When the APK is installed and first launched:

1. **JNI Bridge** extracts the `digibyted-arm64` asset from the APK to app-private storage
2. **NodeService** is started (foreground service for battery optimization)
3. **Config & data directories** are created under app-private storage
4. **digibyted daemon** is executed with appropriate `-conf` and `-datadir` arguments
5. **Node runs** until the app is closed or the service is stopped

## Troubleshooting

### "Missing digibyted asset" during Gradle build
- **Cause:** `./scripts/build-android.sh` did not complete or binaries were not staged
- **Fix:** Rerun `./scripts/build-android.sh` and verify output files exist:
  ```bash
  ls -la android/app/src/main/assets/bin/digibyted-arm64
  ```

### CMake configuration fails with "NDK not found"
- **Cause:** `ANDROID_NDK_HOME` not set or wrong version
- **Fix:** Check `.versions/android.env.sh` and ensure NDK is installed:
  ```bash
  ls $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/
  ```

### "Unsupported ABI" or cross-compilation errors
- **Cause:** Toolchain mismatch or CMake cache issues
- **Fix:** Clean and rebuild:
  ```bash
  rm -rf android/build
  ./scripts/build-android.sh
  ```

### Gradle daemon hangs or build is very slow
- **Cause:** Gradle daemon caching or low memory
- **Fix:** Disable daemon:
  ```bash
  ./gradlew assembleDebug --no-daemon
  ```

### Binary file check shows wrong architecture
- **Cause:** Built for wrong ABI or cross-compilation failed
- **Fix:** Verify NDK toolchain and rebuild:
  ```bash
  file android/app/src/main/jniLibs/arm64-v8a/digibyted
  # Should say: aarch64 with interpreter /system/bin/linker64
  ```

## Customization

### Use a Different DigiByte Core Version
```bash
CORE_REF=v8.25.0 ./scripts/setup-core.sh
./scripts/build-android.sh
```

### Build for a Different API Level
Edit `.versions/android.env.sh`:
```bash
export ANDROID_NDK_API_LEVEL=30  # or 31, 32, etc.
```
Then rebuild:
```bash
rm -rf android/build
./scripts/build-android.sh
```

### Add Additional ABIs
Edit `scripts/build-android.sh` and add to `SUPPORTED_ABIS`:
```bash
SUPPORTED_ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")
```
Then rebuild for all ABIs:
```bash
./scripts/build-android.sh
```

### Enable Additional Features in DigiByte Core
Modify CMake flags in `android/CMakeLists.txt` or `android/toolchain-android.cmake` (e.g., enable ZMQ, UPnP, miniupnpc, etc.).

## Related Documentation

- **For most users:** [`docs/GETTING-STARTED-DEV.md`](GETTING-STARTED-DEV.md)
- **Core setup details:** [`docs/CORE-SETUP.md`](CORE-SETUP.md)
- **Configuration tuning:** [`docs/CONFIGURATION.md`](CONFIGURATION.md)
- **Android app integration:** [`docs/JNI-BRIDGE.md`](JNI-BRIDGE.md)
- **On-device deployment:** [`docs/RUNNING-ON-DEVICE.md`](RUNNING-ON-DEVICE.md)
