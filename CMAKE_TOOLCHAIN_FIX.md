## Critical Fix: CMake Not Using Android NDK Toolchain

### Problem Diagnosis
The build is still producing **x86-64 binaries** instead of arm64-v8a, even with the previous fixes.

**Root Cause**: CMake was not applying the Android NDK toolchain before detecting the C/C++ compiler, so it fell back to the **host system compiler (x86_64 gcc/clang)**.

### Changes Made

#### 1. `android/toolchain-android.cmake` (Enhanced)
- Moved `ANDROID_ABI` and `ANDROID_PLATFORM` cache settings BEFORE including NDK toolchain
- Added verification that `CMAKE_C_COMPILER` is set after NDK toolchain is loaded
- Added diagnostic logging to show which compiler is being used
- This ensures the NDK toolchain is properly applied BEFORE CMake's compiler detection

#### 2. `scripts/build-android.sh` (Enhanced Validation)
- Added explicit logging of `CC` and `CXX` compiler paths
- Added validation that the compiler binary exists and is executable
- Added validation that NDK toolchain file exists
- Force CMake cache deletion to avoid stale host compiler configuration

### How to Fix Now

#### Step 1: Clean Everything
```bash
./scripts/clean-build.sh
```

#### Step 2: Verify NDK Path
```bash
# Check that your NDK is correct
ls -la $ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake
# Should show the file exists
echo "NDK: $ANDROID_NDK_HOME"
```

#### Step 3: Rebuild with Enhanced Diagnostics
```bash
# If running manually:
ABI=arm64-v8a ANDROID_ABI=arm64-v8a ANDROID_NDK_HOME="/path/to/ndk/25.1.8937393" \
  ./scripts/build-android.sh

# Watch the output for:
# [build-android] Compiler CC: /path/to/ndk/.../aarch64-linux-android29-clang
# [build-android] Compiler CXX: /path/to/ndk/.../aarch64-linux-android29-clang++
# Android NDK Toolchain: /path/to/ndk/.../aarch64-linux-android29-clang
```

#### Step 4: Verify Binaries (Critical!)
```bash
# Check the binaries MUST be ARM aarch64, NOT x86_64
file android/app/src/main/assets/bin/digibyted-arm64
file android/app/src/main/assets/bin/digibyte-cli-arm64
file android/app/src/main/jniLibs/arm64-v8a/digibyted

# CORRECT output should say: "ARM aarch64" or similar
# WRONG output would say: "x86-64" or "Intel 80386"

# Use the verification script:
./scripts/verify-build-abi.sh
```

#### Step 5: If Still x86-64
If the binaries are STILL x86-64 after these fixes:

1. Check CMake output for errors about NDK toolchain loading
2. Verify NDK path is correct:
   ```bash
   file $ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake
   # Should be a text file (CMake script)
   ```
3. Check if CMakeCache has stale settings:
   ```bash
   cat android/build/arm64-v8a/CMakeCache.txt | grep CMAKE_C_COMPILER
   # Should show aarch64 clang path, not /usr/bin/cc or similar
   ```
4. Try clearing even more aggressively:
   ```bash
   ./scripts/clean-build.sh
   rm -rf android/build
   ABI=arm64-v8a ANDROID_ABI=arm64-v8a ANDROID_NDK_HOME="/path/to/ndk" ./scripts/build-android.sh
   ```

### Full Rebuild Procedure (If Completely Stuck)
```bash
# 1. Deep clean
./scripts/clean-build.sh
rm -rf android/build
rm -rf android/app/src/main/assets/bin
rm -rf android/app/src/main/jniLibs

# 2. Rebuild core (no gradle)
ABI=arm64-v8a ANDROID_ABI=arm64-v8a ANDROID_NDK_HOME="/path/to/ndk/25.1.8937393" \
  ./scripts/build-android.sh 2>&1 | tee build.log

# 3. Check output
tail -50 build.log | grep -E "(Compiler|NDK Toolchain|CMAKE_C|ERROR)"

# 4. Verify binaries before gradle
./scripts/verify-build-abi.sh

# 5. If verify passes, package APK
cd android
./gradlew clean assembleDebug
cd ..
```

### What the Toolchain Changes Do

**Before (Broken):**
```
CMake starts → Detects host compiler (x86-64 gcc)
             → Sets CMAKE_C_COMPILER = /usr/bin/cc
             → Tries to include NDK toolchain (too late!)
             → Autotools uses wrong compiler
```

**After (Fixed):**
```
CMake starts → Toolchain file loaded FIRST
            → Sets CMAKE_ANDROID_* variables
            → Includes NDK's official toolchain.cmake
            → NDK toolchain sets CMAKE_C_COMPILER = aarch64-clang
            → Autotools gets correct cross-compiler
            → Binary is ARM aarch64
```

### Environment Variables (Must Be Set)

| Variable | Value | Example |
|----------|-------|---------|
| `ANDROID_ABI` | Target ABI | `arm64-v8a` |
| `ABI` | Alternative for `ANDROID_ABI` | `arm64-v8a` |
| `ANDROID_NDK_HOME` | Path to NDK | `/home/user/Android/Sdk/ndk/25.1.8937393` |

### Recommended: Use `./setup.sh`
The interactive setup handles all of this:
```bash
./setup.sh
# - Detects device ABI
# - Finds NDK automatically
# - Passes correct env vars
# - Builds with proper toolchain
# - Stages binaries
# - Deploys to device
```

### Files Modified in This Fix
- `android/toolchain-android.cmake` — Ensures NDK toolchain is loaded first
- `scripts/build-android.sh` — Added validation and diagnostics

### Next Steps
1. Run the clean rebuild procedure above
2. Watch for compiler paths in the output
3. Run `./scripts/verify-build-abi.sh` to confirm ARM binaries
4. Report the output if still seeing x86-64 binaries
