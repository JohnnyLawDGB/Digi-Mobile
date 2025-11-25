# Android build quickstart

This document describes how to cross-compile DigiByte Core v8.26 for Android targets (arm64-v8a, armeabi-v7a).

## Prerequisites
- Android NDK r23+ installed locally (set `ANDROID_NDK_ROOT`).
- Standard build deps for DigiByte/Bitcoin Core (autotools, pkg-config, python3) on the host.
- Git initialized with the DigiByte Core submodule (`./scripts/fetch-core.sh`).

## Steps
1. Ensure the submodule is present and pinned:
   ```bash
   ./scripts/fetch-core.sh
   ```
2. Select architecture and API level (defaults: `ARCH=arm64-v8a`, `API=29`).
3. Run the Android build driver:
   ```bash
   ARCH=arm64-v8a API=29 ANDROID_NDK_ROOT=/path/to/ndk \
     ./scripts/build-android.sh
   ```
4. Artifacts are staged under `android/output/<arch>/bin`.
5. (Optional) Bundle binaries plus configs for deployment:
   ```bash
   ARCH=arm64-v8a ./scripts/make-android-rootfs.sh
   ```

## Notes
- GUI is disabled; only daemon and CLI/tx utilities are built.
- Pruning and other runtime defaults live in `config/android-pruned.conf` and are copied into bundles.
- If you need UPnP, ZMQ, or tests, re-enable the corresponding flags in `build-android.sh` before configuring.
