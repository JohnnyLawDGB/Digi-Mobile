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

log "Removing any lingering libtool .libs directories and .o/.la artifacts"
# Remove .libs directories (libtool intermediate dirs) which can contain host-built objects
find "${ROOT_DIR}" -type d -name ".libs" -prune -print -exec rm -rf {} + || true
# Remove libtool archive files that might be stale
find "${ROOT_DIR}" -type f -name "*.la" -print -delete || true
# Optionally remove any stray object files under android/build (defensive)
find "${ROOT_DIR}/android/build" -type f -name "*.o" -print -delete || true

log "Clean complete. You can now run ./scripts/build-android.sh"
