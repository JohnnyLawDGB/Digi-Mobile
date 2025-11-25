#!/usr/bin/env bash
set -euo pipefail

# Package built binaries and configs into an Android-friendly bundle.
# Usage: ARCH=arm64-v8a ./scripts/make-android-rootfs.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ARCH=${ARCH:-arm64-v8a}
OUTPUT_DIR="$ROOT_DIR/android/output/${ARCH}"
DIST_DIR="$ROOT_DIR/android/dist/${ARCH}"
CONFIG_DIR="$ROOT_DIR/config"

if [[ ! -d "$OUTPUT_DIR" ]]; then
  echo "No build artifacts found at $OUTPUT_DIR" >&2
  exit 1
fi

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR/bin" "$DIST_DIR/config"

cp -v "$OUTPUT_DIR"/bin/digibyted "$DIST_DIR/bin/"
if [[ -f "$OUTPUT_DIR/bin/digibyte-cli" ]]; then
  cp -v "$OUTPUT_DIR"/bin/digibyte-cli "$DIST_DIR/bin/"
fi
if [[ -f "$OUTPUT_DIR/bin/digibyte-tx" ]]; then
  cp -v "$OUTPUT_DIR"/bin/digibyte-tx "$DIST_DIR/bin/"
fi

cp -v "$CONFIG_DIR"/android-pruned.conf "$DIST_DIR/config/"

echo "Bundle ready at $DIST_DIR"
