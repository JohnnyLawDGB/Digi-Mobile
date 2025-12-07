# Android fuzz harness build fix (NDK)

When cross-compiling DigiByte Core's fuzz harnesses with the Android NDK, the build can fail with `cookie_io_functions_t` being undefined because bionic lacks glibc-only APIs like `fopencookie()`. A tiny guard change resolves the issue so the rest of the Android build can proceed.

## Quick steps
1. Initialize the DigiByte Core checkout and apply the guard patch:
   ```bash
   ./init-core-and-patch.sh
   ```
2. Remove any stale Android build artifacts (optional but recommended):
   ```bash
   rm -rf android/build/arm64-v8a/core-build-arm64-v8a
   ```
3. Run the guided setup/build:
   ```bash
   ./setup.sh
   ```
   Use the pruned config (`Y`) and rebuild from source (`N`) when prompted.

## What the patch changes
- File: `android/patches/0001-android-fuzz-util-glibc-guard.patch`
- Change: adds `&& !defined(__ANDROID__)` to the existing guard around the `fopencookie()` usage in `core/src/test/fuzz/util.cpp`.
- Effect: Android builds take the fallback `#else` branch that returns `nullptr`, which is sufficient because fuzz harnesses are compiled (not executed) on Android.

## Verify it applied
```bash
cd core
rg "!defined\(__ANDROID__\)" src/test/fuzz/util.cpp
```
You should see the updated guard in place. Glibc/FreeBSD builds are unchanged.

## Files involved
- `android/patches/0001-android-fuzz-util-glibc-guard.patch` — one-line guard adjustment
- `init-core-and-patch.sh` — initializes the `core/` checkout and applies the patch
