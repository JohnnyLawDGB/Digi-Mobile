#!/usr/bin/env bash
set -euo pipefail

# Wrapper for cross-compiling DigiByte Core for Android via CMake + the NDK.
# Example:
#   ANDROID_NDK_HOME=/path/to/ndk ./scripts/build-android.sh
#   ANDROID_NDK_HOME=/path/to/ndk ARCH=armeabi-v7a API=24 ./scripts/build-android.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="${BUILD_DIR:-${ROOT_DIR}/android/build}"
ARCH="${ARCH:-${ABI:-arm64-v8a}}"
API="${API:-24}"
JOBS="${JOBS:-$(nproc)}"

# Resolve the NDK location from common environment variable names.
if [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
  ANDROID_NDK="${ANDROID_NDK_HOME}"
elif [[ -n "${ANDROID_NDK_ROOT:-}" ]]; then
  ANDROID_NDK="${ANDROID_NDK_ROOT}"
elif [[ -n "${ANDROID_NDK:-}" ]]; then
  ANDROID_NDK="${ANDROID_NDK}"
else
  echo "Set ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) to your NDK path." >&2
  exit 1
fi

TOOLCHAIN_FILE="${ROOT_DIR}/android/toolchain-android.cmake"

cmake -S "${ROOT_DIR}/android" -B "${BUILD_DIR}" \
  -DCMAKE_TOOLCHAIN_FILE="${TOOLCHAIN_FILE}" \
  -DANDROID_NDK="${ANDROID_NDK}" \
  -DANDROID_ABI="${ARCH}" \
  -DANDROID_PLATFORM="${API}" \
  "${@}"

cmake --build "${BUILD_DIR}" --target digibyted -- -j"${JOBS}"

PREFIX="${BUILD_DIR}/android-prefix/${ARCH}"
if [[ -d "${PREFIX}" ]]; then
  echo "Artifacts staged under ${PREFIX}"
else
  echo "Build completed; check ${BUILD_DIR} for logs or artifacts." >&2
fi
