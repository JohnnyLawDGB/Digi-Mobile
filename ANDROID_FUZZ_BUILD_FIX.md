# Android Fuzz Util Build Fix

## Problem

When cross-compiling DigiByte Core for Android (arm64-v8a) using the NDK, the build fails in `core/src/test/fuzz/util.cpp` with:

```
error: unknown type name 'cookie_io_functions_t'
  const cookie_io_functions_t io_hooks = {
```

This occurs because Android's bionic libc does not provide `cookie_io_functions_t` and `fopencookie()`, which are glibc-specific GNU extensions.

## Root Cause

The existing code in `src/test/fuzz/util.cpp` already has a preprocessor guard:

```cpp
#if defined _GNU_SOURCE && (defined(__linux__) || defined(__FreeBSD__))
    const cookie_io_functions_t io_hooks = { ... };
    return fopencookie(this, mode.c_str(), io_hooks);
#else
    (void)mode;
    return nullptr;
#endif
```

However, this guard is insufficient for Android NDK cross-compilation because `__ANDROID__` is defined alongside `__linux__` during the NDK build, causing the compiler to try to process the glibc-specific code.

## Solution

A patch has been created: `android/patches/0001-android-fuzz-util-glibc-guard.patch`

This patch strengthens the preprocessor guard by explicitly excluding Android:

```cpp
#if defined _GNU_SOURCE && (defined(__linux__) || defined(__FreeBSD__)) && !defined(__ANDROID__)
```

Now when compiling for Android, the compiler skips the `cookie_io_functions_t` declaration and uses the safe `#else` fallback that returns `nullptr`.

## Setup & Build

### Step 1: Initialize and Patch the Core Submodule

```bash
cd ~/Digi-Mobile
./init-core-and-patch.sh
```

This script will:
- Initialize the `core/` submodule
- Checkout the pinned version (v8.26)
- Apply the Android fuzz util patch

### Step 2: Clean and Rebuild

```bash
# Remove stale Android build artifacts
rm -rf android/build/arm64-v8a/core-build-arm64-v8a

# Run the full setup/build wizard
./setup.sh
```

When prompted:
- **Use pruned config?** → Y (recommended)
- **Use the prebuilt binary?** → N (to rebuild from source)

The build should now proceed without `cookie_io_functions_t` errors.

## Verification

After applying the patch, verify the change in the core repository:

```bash
cd core
git diff src/test/fuzz/util.cpp
```

You should see the guard updated to include `&& !defined(__ANDROID__)`.

## What the Patch Does

- **Minimal change**: Only modifies the preprocessor condition on one line
- **Preserves Linux/glibc behavior**: Glibc systems (Linux/FreeBSD) continue using `fopencookie()` for proper cookie I/O semantics
- **Enables Android compilation**: Android builds use the `#else` fallback (returns `nullptr`), which is safe because fuzz harnesses don't actually need to run on Android—only compile
- **Non-breaking**: No changes to function signatures, public APIs, or other code

## Notes

- Fuzz harnesses will not actively run on Android (they would return `nullptr` from `FuzzedFileProvider::open()`), but that's acceptable because the goal is to compile the entire project, not to execute fuzzing tests on mobile
- The actual DigiByte Core consensus code is unaffected; only the fuzz test infrastructure is impacted
- This patch is portable and should work on any NDK-based Android cross-compilation setup
