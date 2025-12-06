#!/usr/bin/env bash
# Clean Android build artifacts and rebuild from scratch.
# Use this if you encounter linker errors about incompatible object files.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

log() {
  echo "[clean-build] $*"
}

log "Cleaning Android build artifacts..."
rm -rf "${ROOT_DIR}/android/build"
log "Removed: ${ROOT_DIR}/android/build"

log "Cleaning DigiByte Core build artifacts..."
rm -rf "${ROOT_DIR}/core/depends/work"
rm -rf "${ROOT_DIR}/core/build-android-arm64"
log "Removed Core build directories"

log "Clean complete. You can now run ./scripts/build-android.sh"
