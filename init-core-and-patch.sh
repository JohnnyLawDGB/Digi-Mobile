#!/usr/bin/env bash
# Initialize core submodule with Android-specific patches applied

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
CORE_DIR="$ROOT_DIR/core"

log() { echo "[Digi-Mobile] $*"; }
die() { echo "[Digi-Mobile] ERROR: $*" >&2; exit 1; }

log "Initializing DigiByte Core submodule..."
cd "$ROOT_DIR"
git submodule update --init core || die "Failed to init core submodule"

log "Checking out pinned version..."
CORE_REF=$(<.versions/core-version.txt)
cd "$CORE_DIR"
git fetch --tags >/dev/null 2>&1 || true
git checkout "$CORE_REF" || die "Failed to checkout $CORE_REF"

# Apply any patches from android/patches
PATCH_DIR="$ROOT_DIR/android/patches"
if compgen -G "$PATCH_DIR"/*.patch > /dev/null 2>&1; then
    log "Applying Android-specific patches..."
    for patch in "$PATCH_DIR"/*.patch; do
        log "  Applying $(basename "$patch")"
        git apply "$patch" || die "Failed to apply $patch"
    done
fi

log "✓ Core submodule initialized and patches applied."
log "✓ Ready to build. Run: ./setup.sh"

