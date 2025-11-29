# Ubuntu 22.04 environment and variables for Digi-Mobile builds

These notes collect the environment variables and host dependencies needed to build Digi-Mobile's Android artifacts on Ubuntu 22.04 using the DigiByte Core v8.26.x toolchain.

## Host packages

Install common Bitcoin/DigiByte Core build tools:

```bash
sudo apt-get update
sudo apt-get install -y build-essential autoconf automake libtool pkg-config python3 \
  cmake ninja-build openjdk-17-jdk git adb
```

## Core checkout variables

`scripts/setup-core.sh` uses overridable variables to pull DigiByte Core v8.26.x into `core/`:
- `CORE_DIR` (default `core/`) – location for the checkout.
- `CORE_REMOTE_URL` – upstream remote (defaults to the DigiByte Core GitHub mirror).
- `CORE_REF` – tag or branch to pin (defaults to `v8.26.1`, matching the BTC 0.26 era).

Run `CORE_DIR=/path/to/core CORE_REF=v8.26.1 ./scripts/setup-core.sh` if you need a non-default location or ref.

## Android NDK and cross-compile variables

### Required NDK path
- Export one of `ANDROID_NDK_HOME`, `ANDROID_NDK_ROOT`, or `ANDROID_NDK` to the installed NDK. The build scripts resolve the NDK from these variables and fail early if none are set.
- Use an r26b (or newer) NDK to match the Gradle `ndkVersion` used in the app module.

Example:

```bash
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/25.1.8937393
```

### Architecture and API level
- `ARCH` – target ABI for helper scripts (`arm64-v8a` default; `armeabi-v7a` supported by the toolchain helper).
- `API` / `API_LEVEL` – Android platform version used by the toolchain (defaults to 24 for the Autotools path, 29 in the env helper). Override when building against a different API level.

Examples:

```bash
ARCH=arm64-v8a API=29 ANDROID_NDK_ROOT=$ANDROID_NDK_HOME ./scripts/build-android.sh
API_LEVEL=29 ANDROID_NDK_HOME=$ANDROID_NDK_HOME ./scripts/build-core-android.sh
source android/toolchain/env-setup.sh arm64-v8a 29 $ANDROID_NDK_HOME
```

### Toolchain exports
`scripts/build-core-android.sh` and `android/toolchain/env-setup.sh` populate the usual cross-compile exports (`AR`, `CC`, `CXX`, `LD`, `STRIP`, `SYSROOT`, and PATH). You generally do not need to set them manually; they derive from the NDK path and API/ARCH values above.

## CMake-based builds (android/)

If you prefer the CMake pipeline under `android/`, configure with:

```bash
cmake -S android -B android/build \
  -G "Ninja" \
  -DCMAKE_TOOLCHAIN_FILE=android/toolchain-android.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=24
cmake --build android/build --target digibyted
```

Here `ANDROID_NDK_HOME` (or `-DANDROID_NDK`) must point to the NDK, and you can override `ANDROID_ABI` / `ANDROID_PLATFORM` to match your target.

## Gradle/SDK variables for the APK

The Android app module expects:
- JDK 17 (`JAVA_HOME` should point to OpenJDK 17 on Ubuntu 22.04).
- Android SDK with platform 34 and build-tools matching the Gradle plugin; set `ANDROID_SDK_ROOT` to the SDK install (e.g., `$HOME/Android/Sdk`).
- NDK version `25.1.8937393` installed under the SDK to match `ndkVersion` in `android/app/build.gradle`.

Example exports before running Gradle from `android/`:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/25.1.8937393
./gradlew assembleDebug
```

## Quick checklist for Ubuntu 22.04
- Host packages installed (build-essential, autotools, CMake, Ninja, JDK 17, adb).
- `CORE_DIR`/`CORE_REF` set if deviating from defaults; `./scripts/setup-core.sh` completed.
- `ANDROID_SDK_ROOT` and `JAVA_HOME` exported for Gradle.
- `ANDROID_NDK_HOME` (or `ANDROID_NDK_ROOT`/`ANDROID_NDK`) exported to the r26-era NDK, matching `ndkVersion`.
- `ARCH` and `API`/`API_LEVEL` chosen and passed to the build helpers when overriding defaults.
