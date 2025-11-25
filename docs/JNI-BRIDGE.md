# Digi-Mobile JNI Bridge

_Status: Experimental / Early._ The JNI layer provides a minimal bridge for Android apps to control a truncated/pruned DigiByte node packaged by Digi-Mobile.

## Who this is for
- Android developers wiring native DigiByte control into Kotlin/Java code.
- Contributors interested in evolving the JNI surface without touching consensus logic.

## What you should have before starting
- Android NDK and CMake configured (see [`ANDROID-BUILD`](ANDROID-BUILD.md)).
- The DigiByte daemon built and bundled for your target ABI.
- Comfort reading simple C++ and JNI signatures.

## Purpose
- Provide a stable Java/Kotlin-facing API (`DigiMobileNodeController`) that Android code can invoke.
- Expose simple lifecycle operations (start/stop/status) while the node is still delivered as an external daemon binary.
- Establish the CMake scaffolding needed to build a loadable `libdigimobile_jni.so` for Android app modules.

## Implementation summary
- The JNI layer lives under `android/jni/` and is built as `libdigimobile_jni.so`.
- `DigiMobileNodeController` in `android/java/com/digimobile/node/` loads that library and delegates to native methods.
- Node startup currently shells out to a `digibyted`-like binary via `fork` + `exec` and records the child PID for later stop and status checks.
- Status is coarse-grained: `NOT_RUNNING` when no PID is known, `RUNNING` when the tracked PID is alive. More nuanced states will require RPC and sync awareness.

## Using the controller from Android

```java
DigiMobileNodeController controller = new DigiMobileNodeController();
controller.startNode(configPath, dataDir);
String status = controller.getStatus();
controller.stopNode();
```

- `configPath` and `dataDir` should reside in app-private storage managed by the host application.
- Starting the node may be a blocking call; invoke on a background executor rather than the UI thread.
- The produced `libdigimobile_jni.so` can be bundled in an Android module using Gradle's `externalNativeBuild` or by manually including the shared library in the APK/AAB.

## What could go wrong?
- App sandboxes may restrict spawning external binaries on newer Android versions.
- JNI crashes are hard to debug without symbols; keep build outputs and logcat handy.
- ABI mismatch between the daemon binary and the JNI library will prevent loading.

## How to recover
- Verify ABI/API alignment in [`android/CMakeLists.txt`](../android/CMakeLists.txt) and rebuild.
- Use `adb logcat` to capture native crash output and rebuild with symbols enabled.
- Consider packaging the daemon alongside the JNI library in app-private storage to avoid permission issues.

## Related docs
- [`OVERVIEW`](OVERVIEW.md) for project context.
- [`ANDROID-BUILD`](ANDROID-BUILD.md) for build steps.
- [`CONFIGURATION`](CONFIGURATION.md) to choose a config passed into the JNI bridge.
