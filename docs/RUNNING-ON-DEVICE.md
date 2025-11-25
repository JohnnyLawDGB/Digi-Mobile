# Running DigiByte Core daemon on Android devices

This document describes a lightweight ADB-based workflow for pushing and running the DigiByte Core daemon on an Android device or emulator. It is intended as a low-level test harness and not yet integrated into an Android application.

## Prerequisites

- Android platform-tools (ADB) installed and available on your `PATH`.
- At least one Android device or emulator connected and visible via `adb devices`.
- The Android daemon binary built (arm64-v8a) using the existing pipeline.

## Quickstart

1. **Build the Android binary** (if not already built):
   ```bash
   ./scripts/build-android.sh
   ```

2. **Push the node assets** (binary + config) to the connected device:
   ```bash
   ./scripts/push-node.sh
   ```
   - Defaults:
     - Binary: `./android/build/arm64-v8a/digibyted`
     - Config: `./config/digimobile-pruned.conf`
   - Assets are placed under `/data/local/tmp/digimobile` on the device.

3. **Run the daemon** on the device:
   ```bash
   ./scripts/run-node.sh
   ```
   - Starts the daemon with the pushed config and stores logs under `/data/local/tmp/digimobile/logs` on the device.

4. **Collect logs** back to the host for inspection:
   ```bash
   ./scripts/pull-logs.sh
   ```
   - Logs are copied to `./logs/device/<device_id>/` on the host.

5. **Stop the daemon** when finished:
   ```bash
   ./scripts/stop-node.sh
   ```
   - Currently issues a best-effort process kill on the device. RPC-based shutdown can be added later if enabled in the config.

## Notes

- If multiple devices are connected, the scripts default to the first device reported by `adb devices`. Pass a specific device ID to `device-select.sh` (or set `DEVICE_ID` before calling a script) to target a particular device.
- The target directory `/data/local/tmp/digimobile` is suitable for quick testing and does not require root on most devices, but storage policies may vary.
- Consider adjusting the provided config for pruning, RPC credentials, or network selection to fit your testing scenario.
