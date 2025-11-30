#!/usr/bin/env bash
# Supported Android toolchain: JDK 17, Android SDK Platform 34 with build-tools 34.x, NDK 25.1.8937393, ABI arm64-v8a, API level 29.
# Update .versions/android.env.sh to change the enforced Android toolchain and CMake arguments.

# Orchestrate Digi-Mobile's Android build steps, including compiling DigiByte
# Core for Android arm64-v8a and staging the daemon binary as an asset for the
# APK. This script focuses on wiring up artifacts; it does not alter consensus
# rules.
set -euo pipefail

# Resolve repository root even when invoked via a symlink or from PATH.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
VERSIONS_FILE="${ROOT_DIR}/.versions/android.env.sh"
ENV_SETUP_SCRIPT="${ROOT_DIR}/android/toolchain/env-setup.sh"
CMAKE_BUILD_DIR="${ROOT_DIR}/android/build"
JNI_LIBS_DIR="${ROOT_DIR}/android/app/src/main/jniLibs"
CMAKE_GENERATOR="Ninja"

log() {
  echo "[build-android] $*"
}

die() {
  echo "[build-android] ERROR: $*" >&2
  exit 1
}

[[ -f "${VERSIONS_FILE}" ]] || die "Missing ${VERSIONS_FILE}. Ensure the repository checkout includes the versioned Android toolchain files."
# shellcheck source=/dev/null
source "${VERSIONS_FILE}"
# shellcheck source=/dev/null
source "${ENV_SETUP_SCRIPT}"

ANDROID_PREFIX="${CMAKE_BUILD_DIR}/android-prefix/${ANDROID_ABI}"
BIN_DIR="${ANDROID_PREFIX}/bin"
LIB_DIR="${ANDROID_PREFIX}/lib"
JNI_TARGET_DIR="${JNI_LIBS_DIR}/${ANDROID_ABI}"

log "Configuring DigiByte Core build via CMake (${ANDROID_ABI}, android-${ANDROID_NDK_API_LEVEL})"
cmake -S "${ROOT_DIR}/android" -B "${CMAKE_BUILD_DIR}" \
  -G "${CMAKE_GENERATOR}" \
  -DANDROID_ABI="${ANDROID_ABI}" \
  -DANDROID_PLATFORM="android-${ANDROID_NDK_API_LEVEL}" \
  -DCMAKE_TOOLCHAIN_FILE="${ROOT_DIR}/android/toolchain-android.cmake"

log "Building digibyted for Android"
cmake --build "${CMAKE_BUILD_DIR}" --target digibyted

[[ -d "${BIN_DIR}" ]] || die "digibyted binary missing at ${BIN_DIR}; CMake install step may have failed."

log "Staging native artifacts into ${JNI_TARGET_DIR}"
mkdir -p "${JNI_TARGET_DIR}"
shopt -s nullglob
for binary in "${BIN_DIR}"/*; do
  cp "${binary}" "${JNI_TARGET_DIR}/"
  log "Copied $(basename "${binary}")"
done
if [[ -d "${LIB_DIR}" ]]; then
  for so in "${LIB_DIR}"/*.so; do
    cp "${so}" "${JNI_TARGET_DIR}/"
    log "Copied $(basename "${so}")"
  done
fi
shopt -u nullglob

log "digibyted staged to ${JNI_TARGET_DIR}."
log "Run ./gradlew assembleDebug (from android/) to package the APK with the bundled daemon."
