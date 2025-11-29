> Supported Android toolchain: JDK 17, Android SDK Platform 34 with build-tools 34.x, NDK 25.1.8937393, ABI arm64-v8a, API level 29. Update this file (and the matching .versions/android.env.sh) when bumping versions.

# Android build environment (known-good)

| Component | Version | Notes |
| --- | --- | --- |
| JDK | 17 | JAVA_HOME must point to this JDK. |
| Android SDK Platform | 34 | Install via sdkmanager; build-tools 34.x. |
| Android NDK | 25.1.8937393 | Matches app module ndkVersion and CMake checks. |
| ABI | arm64-v8a | The packaged APK currently targets arm64 only. |
| NDK platform/API level | 29 | Passed to CMake/Gradle as ANDROID_PLATFORM=android-29. |

Set `JAVA_HOME`, `ANDROID_SDK_ROOT`, and `ANDROID_NDK_HOME` to your installations before running the helper scripts. Use `.versions/android.env.sh` as the single source of truth for scripting the versions above.
