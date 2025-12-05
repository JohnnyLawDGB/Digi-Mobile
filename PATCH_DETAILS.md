# Patch Details: Android Fuzz Util Build Fix

## File Modified
- `src/test/fuzz/util.cpp` (in the core submodule)

## Change Summary
**One line change** in the `FuzzedFileProvider::open()` function at approximately line 234.

### Before
```cpp
#if defined _GNU_SOURCE && (defined(__linux__) || defined(__FreeBSD__))
```

### After
```cpp
#if defined _GNU_SOURCE && (defined(__linux__) || defined(__FreeBSD__)) && !defined(__ANDROID__)
```

## Impact Analysis

### On Linux/glibc/FreeBSD (no change in behavior)
- ✓ `__ANDROID__` is not defined
- ✓ Condition still evaluates to true
- ✓ `cookie_io_functions_t` code path is taken
- ✓ `fopencookie()` is used (proper semantics preserved)

### On Android NDK (behavior change)
- ✓ `__ANDROID__` is defined  
- ✓ Condition now evaluates to false
- ✓ `cookie_io_functions_t` code is skipped by preprocessor
- ✓ `#else` fallback is used (returns `nullptr`)
- ✓ Compilation succeeds (no undefined type error)

### On Other Platforms
- ✓ No impact (condition behavior unchanged)

## Why This Works

1. **Problem**: NDK cross-compiler defines both `__ANDROID__` and `__linux__`, so the existing guard doesn't help
2. **Solution**: Explicitly reject the glibc path when `__ANDROID__` is defined
3. **Fallback**: The existing `#else` branch is safe—it returns `nullptr`, which is acceptable because:
   - Fuzz tests only need to compile, not run, on Android
   - The fallback is a no-op that doesn't break anything
   - The real consensus code is unaffected

## Testing the Patch

```bash
# After applying init-core-and-patch.sh, verify:
cd ~/Digi-Mobile/core
git diff src/test/fuzz/util.cpp | head -30

# Expected: You should see the line with +&& !defined(__ANDROID__)
```

## Reverting the Patch (if needed)

```bash
cd ~/Digi-Mobile/core
git apply -R ../android/patches/0001-android-fuzz-util-glibc-guard.patch
```
