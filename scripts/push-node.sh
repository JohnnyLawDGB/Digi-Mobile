#!/usr/bin/env bash
# Push the DigiByte daemon binary and configuration to a connected Android device.
# Environment: requires adb on PATH, at least one connected device, and a built
# digibyted binary under android/build. Intended for quick testing, not
# production deployment.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

log() { echo "[Digi-Mobile] $*"; }

die() {
  echo "[Digi-Mobile] ERROR: $*" >&2
  exit 1
}

require_cmd() { command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"; }

require_cmd adb

BINARY_PATH=${1:-"$REPO_ROOT/android/build/arm64-v8a/digibyted"}
CONFIG_PATH=${2:-"$REPO_ROOT/config/digimobile-pruned.conf"}
TARGET_DIR="/data/local/tmp/digimobile"

source "$SCRIPT_DIR/device-select.sh" "${DEVICE_ID:-}" >/dev/null
ADB=(adb -s "$SELECTED_DEVICE")

[[ -f "$BINARY_PATH" ]] || die "Binary not found at $BINARY_PATH. Run build-android.sh first."
[[ -f "$CONFIG_PATH" ]] || die "Config not found at $CONFIG_PATH. Choose a config under config/."

log "Pushing binary and config to $SELECTED_DEVICE"
"${ADB[@]}" shell "mkdir -p $TARGET_DIR/logs $TARGET_DIR/data" || die "Failed to create target directories on device"
"${ADB[@]}" push "$BINARY_PATH" "$TARGET_DIR/digibyted"
"${ADB[@]}" push "$CONFIG_PATH" "$TARGET_DIR/digimobile.conf"
"${ADB[@]}" shell "chmod +x $TARGET_DIR/digibyted"

cat <<INFO
[Digi-Mobile] Pushed assets to device $SELECTED_DEVICE:
  Binary: $TARGET_DIR/digibyted
  Config: $TARGET_DIR/digimobile.conf
  Data dir: $TARGET_DIR/data
  Logs: $TARGET_DIR/logs
INFO
