#!/usr/bin/env bash
# Build DigiByte Core for Android via CMake and the NDK.
# Environment: requires ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) set, CMake on PATH,
# and the DigiByte Core submodule initialized under ../core. Designed for local
# experimentation; does not change DigiByte consensus logic.
# TODO: Add support for additional ABIs beyond arm64-v8a and armeabi-v7a.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="${BUILD_DIR:-${ROOT_DIR}/android/build}"
ARCH="${ARCH:-${ABI:-arm64-v8a}}"
API="${API:-24}"
JOBS="${JOBS:-$(nproc)}"

log() {
  echo "[Digi-Mobile] $*"
}

die() {
  echo "[Digi-Mobile] ERROR: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

require_cmd cmake
require_cmd nproc

[[ -d "${ROOT_DIR}/core" ]] || die "core/ submodule not found. Run ./scripts/setup-core.sh first."

resolve_ndk() {
  if [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
    echo "${ANDROID_NDK_HOME}"
  elif [[ -n "${ANDROID_NDK_ROOT:-}" ]]; then
    echo "${ANDROID_NDK_ROOT}"
  elif [[ -n "${ANDROID_NDK:-}" ]]; then
    echo "${ANDROID_NDK}"
  else
    die "Set ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) to your NDK path."
  fi
}

ANDROID_NDK="$(resolve_ndk)"
[[ -d "${ANDROID_NDK}" ]] || die "Resolved NDK path ${ANDROID_NDK} does not exist"

case "${ARCH}" in
  arm64-v8a|armeabi-v7a) ;;
  *) die "Unsupported ARCH '${ARCH}'. Update the script or set a supported ABI." ;;
esac

TOOLCHAIN_FILE="${ROOT_DIR}/android/toolchain-android.cmake"
[[ -f "${TOOLCHAIN_FILE}" ]] || die "Toolchain file missing at ${TOOLCHAIN_FILE}"

log "Configuring DigiByte Core for ${ARCH} (API ${API}) using ${ANDROID_NDK}"
cmake -S "${ROOT_DIR}/android" -B "${BUILD_DIR}" \
  -DCMAKE_TOOLCHAIN_FILE="${TOOLCHAIN_FILE}" \
  -DANDROID_NDK="${ANDROID_NDK}" \
  -DANDROID_ABI="${ARCH}" \
  -DANDROID_PLATFORM="${API}" \
  "${@}"

log "Building digibyted with ${JOBS} parallel jobs"
cmake --build "${BUILD_DIR}" --target digibyted -- -j"${JOBS}"

PREFIX="${BUILD_DIR}/android-prefix/${ARCH}"
if [[ -d "${PREFIX}" ]]; then
  log "Artifacts staged under ${PREFIX}"
else
  log "Build completed; check ${BUILD_DIR} for logs or artifacts."
fi
