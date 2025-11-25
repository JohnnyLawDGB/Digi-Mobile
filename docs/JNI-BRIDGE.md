# Digi-Mobile JNI Bridge

This document outlines the initial JNI layer that lets an Android client start, stop, and query the status of a Digi-Mobile node. The current implementation focuses on wiring and is intentionally minimal so future iterations can evolve the design without breaking callers.

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

## Limitations and future work

- Spawning a child process from an Android app is acceptable for internal builds but may be incompatible with Play Store policies. Long-term, we expect to embed DigiByte Core as a library and expose a more controlled runtime.
- The current status reporting does not include sync progress or health checks. A future version should reuse the node's RPC interface for richer telemetry.
- Error handling and lifecycle coordination with Android services/foreground notifications are out of scope for this first iteration.
