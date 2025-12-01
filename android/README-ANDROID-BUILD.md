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
# The Gradle wrapper lives at the repository root; android/ includes a helper script for convenience.
cd android && ./gradlew assembleDebug
```

`build-android.sh` runs DigiByte Core's `depends` build (with QT disabled) before invoking Autotools so that libevent and other native dependencies are built for the Android toolchain automatically.

Artifacts appear in `android/app/src/main/jniLibs/arm64-v8a/` after `build-android.sh` runs.

## Packaging the JNI bridge into the Android app

1. Decode the Gradle wrapper JAR if you haven't already:
   ```bash
   ./scripts/prepare-gradle-wrapper.sh
   ```
2. Ensure your Android SDK/NDK paths are available to Gradle via `ANDROID_SDK_ROOT` / `ANDROID_NDK_HOME` or `local.properties`.
3. Build the APKs (Gradle drives CMake to compile `libdigimobile_jni.so` for `arm64-v8a` and `armeabi-v7a`):
   ```bash
   ./gradlew :android:app:assembleDebug
   ./gradlew :android:app:assembleRelease
   ```
4. Verify the shared library is packaged in the APK (example for debug):
   ```bash
   unzip -l android/app/build/outputs/apk/debug/app-debug.apk | grep digimobile_jni
   ```
   You should see `lib/arm64-v8a/libdigimobile_jni.so` (and `armeabi-v7a` if that ABI was built).
5. For Samsung SM-N950U (64-bit) builds, keep the default `arm64-v8a` ABI filter in `android/app/build.gradle`; `armeabi-v7a` is
   also produced for older 32-bit devices.

## Build flags
- Size-focused defaults: `-Os -ffunction-sections -fdata-sections` with `--gc-sections` during link.
- GUI, tests, man pages, benchmarks, miniupnpc, and ZMQ are disabled to keep the Android daemon lean.
- Optional link-time optimisation can be enabled with `-DDGB_ENABLE_LTO=ON` if the chosen NDK revision supports it.
