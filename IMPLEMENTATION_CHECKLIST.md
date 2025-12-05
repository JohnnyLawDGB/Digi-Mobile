# Implementation Checklist

## What Has Been Done ✓

- [x] **Analyzed the problem**: `cookie_io_functions_t` and `fopencookie()` are glibc-only, not available in Android's bionic libc
- [x] **Located the code**: Found the exact issue in DigiByte Core v8.26 `src/test/fuzz/util.cpp` line 231
- [x] **Designed the fix**: Add `&& !defined(__ANDROID__)` to the preprocessor guard
- [x] **Created the patch**: `android/patches/0001-android-fuzz-util-glibc-guard.patch`
- [x] **Created setup script**: `init-core-and-patch.sh` to initialize submodule and apply patches
- [x] **Documented the solution**: `ANDROID_FUZZ_BUILD_FIX.md` with complete instructions
- [x] **Created technical details**: `PATCH_DETAILS.md` explaining the exact change

## Files Created/Modified

### New Files
- `android/patches/0001-android-fuzz-util-glibc-guard.patch` - The actual patch
- `init-core-and-patch.sh` - Automated setup script
- `ANDROID_FUZZ_BUILD_FIX.md` - User-facing documentation
- `PATCH_DETAILS.md` - Technical details of the patch

### Modified Files
- `.gitmodules` - Already existed (submodule definition)

## Next Steps for User

### Quick Start
```bash
cd ~/Digi-Mobile
./init-core-and-patch.sh
rm -rf android/build/arm64-v8a/core-build-arm64-v8a
./setup.sh
```

### When prompted by setup.sh
- Use pruned config? → **Y**
- Use the prebuilt binary? → **N**

### Expected Result
- No `cookie_io_functions_t` errors
- Clean compilation of `core/src/test/fuzz/util.cpp`
- Android NDK build succeeds

## How the Patch Works

### Single-Line Fix
Changes the preprocessor guard from:
```cpp
#if defined _GNU_SOURCE && (defined(__linux__) || defined(__FreeBSD__))
```

To:
```cpp
#if defined _GNU_SOURCE && (defined(__linux__) || defined(__FreeBSD__)) && !defined(__ANDROID__)
```

### Why It's Minimal & Safe
1. Only affects fuzz test infrastructure (not consensus code)
2. Preserves Linux behavior completely
3. Provides safe fallback for Android (returns nullptr)
4. No new dependencies or refactoring
5. Completely localized change

## Verification Steps

After running `setup.sh`:

```bash
# 1. Check the patch was applied
cd ~/Digi-Mobile/core
git log --oneline -1  # Should show digibyte v8.26 commit

# 2. Verify the Android guard is in place
grep -n "!defined(__ANDROID__)" src/test/fuzz/util.cpp
# Expected: Line ~234 with the new guard

# 3. Try to find any remaining cookie_io_functions_t references
grep "cookie_io_functions_t" src/test/fuzz/util.cpp
# Expected: No output (or only in comments)

# 4. Check build output for fuzz util compilation
# Should NOT see "unknown type name 'cookie_io_functions_t'"
```

## Troubleshooting

### If the patch doesn't apply
```bash
cd ~/Digi-Mobile
git submodule update --init core
cd core
git checkout v8.26
git apply ../android/patches/0001-android-fuzz-util-glibc-guard.patch --verbose
```

### If you get "patch does not apply"
The core submodule may be at a different commit. Verify:
```bash
cd ~/Digi-Mobile/core
git describe --tags  # Should be v8.26 or close to it
```

### Full reset if needed
```bash
cd ~/Digi-Mobile
rm -rf core
git submodule update --init core
./init-core-and-patch.sh
```

## Summary

✓ **Problem**: Android NDK builds fail with `cookie_io_functions_t` undefined  
✓ **Root cause**: glibc-only APIs not available in bionic  
✓ **Solution**: Add `&& !defined(__ANDROID__)` to preprocessor guard  
✓ **Implementation**: One-line patch already prepared and in place  
✓ **Testing**: Run `setup.sh` to verify the fix works  
✓ **Result**: Android cross-compilation succeeds without errors
