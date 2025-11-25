# Android cross-compilation

This directory contains a CMake-based pipeline for cross-compiling the DigiByte Core daemon for Android using the Android NDK. The build produces the headless `digibyted` binary; UI and JNI layers can be layered on later.

## Prerequisites
- **Android NDK**: r26b or newer is recommended. Set `ANDROID_NDK_HOME` or pass `-DANDROID_NDK` to CMake.
- **CMake**: 3.22+ (Ninja or Unix Makefiles generator works).
- **Make**: used to drive the DigiByte Core Autotools build.

## Configure and build with CMake
From the repository root:

```bash
cmake -S android -B android/build \
  -G "Ninja" \
  -DCMAKE_TOOLCHAIN_FILE=android/toolchain-android.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=24
cmake --build android/build --target digibyted
```

### Selecting ABI and API level
- **ABI**: `-DANDROID_ABI=arm64-v8a` (default) or `armeabi-v7a`.
- **API level**: `-DANDROID_PLATFORM=24` by default; raise it if your NDK or device requires a newer platform.

### Where artifacts land
The install prefix defaults to `android/build/android-prefix/<ABI>/`. The `bin/` directory inside that prefix contains the `digibyted` binary alongside its libraries.

## Build flags
- Size-focused defaults: `-Os -ffunction-sections -fdata-sections` with `--gc-sections` during link.
- GUI, tests, man pages, benchmarks, miniupnpc, and ZMQ are disabled to keep the Android daemon lean.
- Optional link-time optimisation can be enabled with `-DDGB_ENABLE_LTO=ON` if the chosen NDK revision supports it.

## Example with the helper script
The `scripts/build-android.sh` wrapper configures and builds for `arm64-v8a` using the toolchain file above:

```bash
ANDROID_NDK_HOME=/path/to/android-ndk ./scripts/build-android.sh
```

Artifacts appear in `android/build/android-prefix/arm64-v8a/` by default.
