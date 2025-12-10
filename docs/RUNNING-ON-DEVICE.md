# Running Digi-Mobile on Android devices

_Status: Experimental / Early._ Digi-Mobile provides adb-based helpers for pushing and running a truncated/pruned DigiByte node on Android. These scripts are intended for hobby testing, not production apps.

## Who this is for
- Developers verifying Android builds on physical devices or emulators.
- Power users comfortable with adb and command-line workflows.

## What you should have before starting
- Android platform-tools (adb) installed and on your `PATH`.
- At least one device or emulator visible via `adb devices`.
- The Android daemon binary built using [`scripts/build-android.sh`](../scripts/build-android.sh).
- A configuration picked from [`CONFIGURATION`](CONFIGURATION.md).

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

## What could go wrong?
- adb cannot find a device (`adb devices` is empty).
- The binary is missing because the build failed or the wrong ABI was used.
- Permissions on `/data/local/tmp` vary across devices; some ROMs may block execution.

## How to recover
- Re-run `adb devices` and ensure USB debugging/emulator is active; pass `DEVICE_ID` to target a specific device.
- Rebuild for the correct ABI/API combination and repush assets.
- If the daemon fails to start, inspect logs with `pull-logs.sh` or `adb logcat` for SELinux denials.

## Related docs
- [`OVERVIEW`](OVERVIEW.md) for project context.
- [`CONFIGURATION`](CONFIGURATION.md) to choose a profile before pushing.
