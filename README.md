# Digi-Mobile

Digi-Mobile packages DigiByte Core v8.26 as a pruned Android-friendly node.
It keeps consensus and P2P behavior identical to upstream while focusing on
cross-compiling, packaging, and operating a lightweight daemon on Android
devices.

## Repository layout
- `core/` – Vendored DigiByte Core source (unchanged consensus/P2P).
- `android/` – NDK/CMake toolchain helpers, JNI/Gradle placeholders, and build artifacts.
- `scripts/` – Convenience wrappers for fetching sources and driving Android builds.
- `config/` – Example runtime configs tuned for pruned mobile nodes.
- `docs/` – Build, deployment, and pruning guidance.

## Quick start
1. Fetch/pin the DigiByte Core sources:
   ```bash
   ./scripts/fetch-core.sh
   ```
2. Build for your target (defaults: `ARCH=arm64-v8a`, `API=29`):
   ```bash
   ANDROID_NDK_ROOT=/path/to/ndk ARCH=arm64-v8a API=29 \
     ./scripts/build-android.sh
   ```
3. Bundle binaries and configs for deployment:
   ```bash
   ARCH=arm64-v8a ./scripts/make-android-rootfs.sh
   ```
4. Push/run on device; see `docs/ANDROID-INSTALL.md` for adb/Termux steps.

## Pruned defaults
Runtime defaults live in `config/android-pruned.conf`, targeting ~2 GiB of
pruned storage with mobile-friendly networking and cache settings. Adjust
paths (e.g., app-private datadir) and cache sizes to fit your device. Pruning
does not alter consensus; it only bounds local disk usage.

## Notes
- No protocol changes: DigiByte Core consensus and network behavior remain upstream.
- GUI is disabled; only daemon/CLI/tx utilities are built for Android.
- For architecture or integration details, consult `docs/ANDROID-BUILD.md` and `docs/PRUNING.md`.
