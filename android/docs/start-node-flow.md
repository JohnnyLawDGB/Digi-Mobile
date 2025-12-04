# Start node flow notes

This document summarizes the current code path for the start-node UI and background service at the time of investigation. No behavior changes were made; this file is only a reference for future work.

## Current entry points
- The start button lives in `app/src/main/res/layout/activity_main.xml` as `@+id/buttonStartNode`. `MainActivity` wires it to `startNodeFlow()` and shows status text via `textStatus`.
- `startNodeFlow()` invokes `NodeBootstrapper.ensureBootstrap()` and starts `NodeService`, then polls `DigiMobileNodeController.getStatus()` for a RUNNING state to update the UI.
- `NodeService` executes `DigiMobileNodeController.startNode()` on a background coroutine to spawn the DigiByte process via JNI.

## Native process control
- `DigiMobileNodeController` loads the `digimobile_jni` library and exposes `startNode`, `stopNode`, and `getStatus` wrappers.
- The JNI bridge (`jni/digimobile_jni.cpp`) copies the `digibyted` binary from assets if needed, forks/execs it with config/datadir args, tracks the PID, and reports status strings such as `RUNNING`, `NOT_RUNNING`, `BINARY_MISSING`, or `ERROR`.
