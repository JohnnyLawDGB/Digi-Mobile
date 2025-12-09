## Fix for X86 Binaries Being Built Instead of ARM64

> **DEPRECATED — historical troubleshooting notes.** The supported build path is `./setup.sh`, which already enforces arm64 builds and ABI verification for the current pre-release.

### Problem
The build system was sometimes producing x86/x86_64 binaries instead of arm64-v8a, rendering the APK non-functional on ARM64 devices.

### Root Cause
1. **Gradle's `ndk.abiFilters`** was hardcoded to `["armeabi-v7a", "arm64-v8a"]` instead of respecting the `ANDROID_ABI` environment variable
2. **`setup.sh`** was not explicitly passing `ABI` and `ANDROID_ABI` to `build-android.sh`
3. **Gradle** would default to building JNI libraries for all listed ABIs if no explicit ABI was set

### Changes Made

#### 1. `android/app/build.gradle` (Line 36-39)
**Before:**
```gradle
ndk {
    abiFilters "armeabi-v7a", "arm64-v8a"
}
```

**After:**
```gradle
ndk {
    // Respect ANDROID_ABI environment variable; default to arm64-v8a if not set.
    // This ensures Gradle only builds native components (JNI) for the same ABI
    // that the cross-compiled daemon was built for.
    abiFilters = targetAbis
}
```

**Effect:** Gradle now only builds JNI libraries for the ABI specified by `ANDROID_ABI` environment variable, defaulting to `arm64-v8a`.

#### 2. `setup.sh` (Line 134)
**Before:**
```bash
ANDROID_NDK_HOME="$NDK_PATH" "$REPO_ROOT/scripts/build-android.sh"
```

**After:**
```bash
# Explicitly pass ABI and NDK to ensure correct architecture is built
ABI="$SELECTED_ABI" ANDROID_ABI="$SELECTED_ABI" ANDROID_NDK_HOME="$NDK_PATH" "$REPO_ROOT/scripts/build-android.sh"
```

**Effect:** `setup.sh` now explicitly passes the detected device ABI to the build script, ensuring the correct architecture is cross-compiled.

#### 3. New Script: `scripts/verify-build-abi.sh`
A new diagnostic tool to verify that built binaries are the correct architecture.

**Usage:**
```bash
./scripts/verify-build-abi.sh
```

**Example output (correct):**
```
[verify-build-abi] Verifying build artifacts for ABI: arm64-v8a

[verify-build-abi] /path/to/digibyted-arm64
[verify-build-abi]   File type: ELF 64-bit LSB executable, ARM aarch64, version 1 ...
[verify-build-abi]   ✓ Correct: arm64-v8a (aarch64)

[verify-build-abi] ✓ All checked files have correct architecture.
```

#### 4. `docs/ADVANCED-ANDROID-BUILD.md`
Added comprehensive troubleshooting section for "Binary file check shows wrong architecture (e.g., x86 instead of arm64)" with step-by-step fixes.

### How to Use

#### Option 1: Run `setup.sh` (Recommended)
The interactive setup now correctly handles ABI detection and passes it to the build:
```bash
./setup.sh
```
- Detects device ABI via `adb`
- Passes ABI explicitly to `build-android.sh`
- Stages correct binaries for Gradle
- Packages and deploys to device

#### Option 2: Manual Build with Explicit ABI
If building manually without `setup.sh`:
```bash
# Clean previous builds
./scripts/clean-build.sh

# Build for a specific ABI (explicit is now recommended)
ABI=arm64-v8a ANDROID_ABI=arm64-v8a ./scripts/build-android.sh

# Verify the binaries are correct
./scripts/verify-build-abi.sh

# Package the APK
cd android
./gradlew assembleDebug
```

#### Option 3: Diagnosis & Recovery
If you suspect x86 binaries were built:
```bash
# 1. Check what was built
./scripts/verify-build-abi.sh

# 2. If wrong ABI, clean and rebuild
./scripts/clean-build.sh
ABI=arm64-v8a ANDROID_ABI=arm64-v8a ./scripts/build-android.sh

# 3. Verify again
./scripts/verify-build-abi.sh

# 4. Rebuild APK
cd android
./gradlew clean assembleDebug
```

### Environment Variables

| Variable | Purpose | Default | Required |
|----------|---------|---------|----------|
| `ANDROID_ABI` | Target ABI for Gradle and build scripts | `arm64-v8a` | No (has default) |
| `ABI` | Alternative name for `ANDROID_ABI` (used by build script) | `arm64-v8a` | No (has default) |
| `ANDROID_NDK_HOME` | Path to Android NDK | Auto-detected | No (auto-detected if not set) |

### Key Takeaways

1. **Always use `./setup.sh`** for most use cases — it handles ABI detection automatically
2. **If building manually, always set `ANDROID_ABI`** explicitly:
   ```bash
   ABI=arm64-v8a ANDROID_ABI=arm64-v8a ./scripts/build-android.sh
   ```
3. **Run `./scripts/verify-build-abi.sh`** after building to confirm the correct architecture
4. **Clean before switching architectures:**
   ```bash
   ./scripts/clean-build.sh
   ```

### Files Modified
- `android/app/build.gradle` — Respect `ANDROID_ABI` env var in `ndk.abiFilters`
- `setup.sh` — Pass ABI explicitly to `build-android.sh`
- `scripts/verify-build-abi.sh` — NEW diagnostic tool
- `docs/ADVANCED-ANDROID-BUILD.md` — Added troubleshooting section

### Testing
To verify the fix works:
```bash
# Clean build
./scripts/clean-build.sh

# Rebuild with explicit ABI
ABI=arm64-v8a ANDROID_ABI=arm64-v8a ./scripts/build-android.sh

# Verify
./scripts/verify-build-abi.sh

# Should show ✓ Correct: arm64-v8a (aarch64) for all binaries
```

If still seeing x86 errors, run:
```bash
file android/app/src/main/assets/bin/digibyted-arm64
# Should say: ELF 64-bit LSB executable, ARM aarch64, ...
# If it says x86_64 or Intel 80386, the build is still targeting wrong arch
```
