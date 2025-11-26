#!/usr/bin/env bash
# Cross-compile DigiByte Core's headless daemon for Android (arm64-v8a).
#
# This script is intentionally focused on producing the digibyted binary for
# embedding inside the Digi-Mobile Android APK. It does not modify consensus
# logic. Only the daemon is built (no GUI, tests, or benchmarks).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CORE_DIR="${ROOT_DIR}/core"
ANDROID_BUILD_DIR="${CORE_DIR}/build-android-arm64"
OUTPUT_DIR="${ROOT_DIR}/build-android/bin/arm64-v8a"
API_LEVEL="${API_LEVEL:-24}"

log() {
  echo "[build-core-android] $*"
}

die() {
  echo "[build-core-android] ERROR: $*" >&2
  exit 1
}

resolve_ndk() {
  if [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
    echo "${ANDROID_NDK_HOME}"
  elif [[ -n "${ANDROID_NDK_ROOT:-}" ]]; then
    echo "${ANDROID_NDK_ROOT}"
  elif [[ -n "${ANDROID_NDK:-}" ]]; then
    echo "${ANDROID_NDK}"
  else
    die "ANDROID_NDK_HOME is not set. Export ANDROID_NDK_HOME to your installed NDK."
  fi
}

log "Ensuring DigiByte Core repository is present via setup-core.sh"
"${ROOT_DIR}/scripts/setup-core.sh"

ANDROID_NDK="$(resolve_ndk)"
[[ -d "${ANDROID_NDK}" ]] || die "Resolved NDK path ${ANDROID_NDK} does not exist"

HOST_OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
TOOLCHAIN="${ANDROID_NDK}/toolchains/llvm/prebuilt/${HOST_OS}-x86_64"
[[ -d "${TOOLCHAIN}" ]] || die "Expected toolchain under ${TOOLCHAIN}"

mkdir -p "${ANDROID_BUILD_DIR}" "${OUTPUT_DIR}"

log "Preparing DigiByte Core build system (autogen)"
pushd "${CORE_DIR}" >/dev/null
./autogen.sh
popd >/dev/null

log "Configuring cross-compile for arm64-v8a (API ${API_LEVEL})"
export AR="${TOOLCHAIN}/bin/llvm-ar"
export CC="${TOOLCHAIN}/bin/aarch64-linux-android${API_LEVEL}-clang"
export CXX="${TOOLCHAIN}/bin/aarch64-linux-android${API_LEVEL}-clang++"
export LD="${TOOLCHAIN}/bin/ld"
export STRIP="${TOOLCHAIN}/bin/llvm-strip"

pushd "${ANDROID_BUILD_DIR}" >/dev/null
# We only need the headless daemon for Android; GUI, tests, and benches are disabled.
../configure \
  --host=aarch64-linux-android \
  --without-gui \
  --disable-tests \
  --disable-bench \
  --with-daemon \
  CXXFLAGS="-fPIC"

log "Building DigiByte Core for Android (this may take a while)"
make -j"$(nproc)"

BIN_SOURCE="src/digibyted"
[[ -x "${BIN_SOURCE}" ]] || die "digibyted not produced at ${BIN_SOURCE}"

cp "${BIN_SOURCE}" "${OUTPUT_DIR}/digibyted"
popd >/dev/null

log "Success: copied src/digibyted -> build-android/bin/arm64-v8a/digibyted"
