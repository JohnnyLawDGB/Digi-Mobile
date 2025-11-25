#!/usr/bin/env bash
# Package built binaries and configs into an Android-friendly bundle.
# Environment: assumes build artifacts exist under android/output/<ABI> and
# configs under config/. Use after build-android.sh succeeds.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ARCH=${ARCH:-arm64-v8a}
OUTPUT_DIR="$ROOT_DIR/android/output/${ARCH}"
DIST_DIR="$ROOT_DIR/android/dist/${ARCH}"
CONFIG_DIR="$ROOT_DIR/config"

log() { echo "[Digi-Mobile] $*"; }

die() {
  echo "[Digi-Mobile] ERROR: $*" >&2
  exit 1
}

[[ -d "$OUTPUT_DIR" ]] || die "No build artifacts found at $OUTPUT_DIR. Run build-android.sh first."
[[ -x "$OUTPUT_DIR/bin/digibyted" ]] || die "digibyted missing under $OUTPUT_DIR/bin"

log "Preparing bundle for ABI ${ARCH}"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR/bin" "$DIST_DIR/config"

cp -v "$OUTPUT_DIR"/bin/digibyted "$DIST_DIR/bin/"
if [[ -f "$OUTPUT_DIR/bin/digibyte-cli" ]]; then
  cp -v "$OUTPUT_DIR"/bin/digibyte-cli "$DIST_DIR/bin/"
fi
if [[ -f "$OUTPUT_DIR/bin/digibyte-tx" ]]; then
  cp -v "$OUTPUT_DIR/bin/digibyte-tx" "$DIST_DIR/bin/"
fi

cp -v "$CONFIG_DIR"/android-pruned.conf "$DIST_DIR/config/" || log "android-pruned.conf not found; copy desired config manually"

log "Bundle ready at $DIST_DIR"
# TODO: Add support for additional ABIs and signing metadata.
