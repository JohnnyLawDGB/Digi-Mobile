#!/usr/bin/env bash
# Orchestrate Digi-Mobile's Android build steps, including compiling DigiByte
# Core for Android arm64-v8a and staging the daemon binary as an asset for the
# APK. This script focuses on wiring up artifacts; it does not alter consensus
# rules.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ASSET_BIN_DIR="${ROOT_DIR}/android/app/src/main/assets/bin"
BINARY_SOURCE="${ROOT_DIR}/build-android/bin/arm64-v8a/digibyted"

log() {
  echo "[build-android] $*"
}

die() {
  echo "[build-android] ERROR: $*" >&2
  exit 1
}

log "Building DigiByte Core daemon for Android (arm64-v8a)"
"${ROOT_DIR}/scripts/build-core-android.sh"

[[ -x "${BINARY_SOURCE}" ]] || die "digibyted binary missing at ${BINARY_SOURCE}"

log "Staging daemon binary into APK assets"
mkdir -p "${ASSET_BIN_DIR}"
cp "${BINARY_SOURCE}" "${ASSET_BIN_DIR}/digibyted-arm64"

log "digibyted staged to ${ASSET_BIN_DIR}/digibyted-arm64"
log "Run ./gradlew assembleDebug (from android/) to package the APK with the bundled daemon."
