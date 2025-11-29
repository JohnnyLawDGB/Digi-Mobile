> Toolchain: JDK 17, Android SDK Platform 34 with build-tools 34.x, NDK 25.1.8937393, ABI arm64-v8a, API 29. Update .versions/android.env.sh and android/toolchain/env-setup.sh to change versions.

# Android cross-compilation

This directory contains a CMake-based pipeline for cross-compiling the DigiByte Core daemon for Android using the Android NDK. The build produces the headless `digibyted` binary; UI and JNI layers can be layered on later.

## Prerequisites
- **Java**: JDK 17 (set `JAVA_HOME`).
- **Android SDK**: Platform 34 with build-tools 34.x (`ANDROID_SDK_ROOT` must point to the SDK root).
- **Android NDK**: 25.1.8937393 (`ANDROID_NDK_HOME` checked by scripts/tooling).
- **CMake**: 3.22+ (Ninja or Unix Makefiles generator works).
- **Make**: used to drive the DigiByte Core Autotools build.

## Copy-paste build flow (arm64-v8a)
From the repository root:

```bash
./scripts/setup-core.sh
source .versions/android.env.sh
./scripts/build-android.sh
cd android && ./gradlew assembleDebug
```

Artifacts appear in `android/app/src/main/jniLibs/arm64-v8a/` after `build-android.sh` runs.

## Build flags
- Size-focused defaults: `-Os -ffunction-sections -fdata-sections` with `--gc-sections` during link.
- GUI, tests, man pages, benchmarks, miniupnpc, and ZMQ are disabled to keep the Android daemon lean.
- Optional link-time optimisation can be enabled with `-DDGB_ENABLE_LTO=ON` if the chosen NDK revision supports it.
