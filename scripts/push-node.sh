#!/usr/bin/env bash
# Push the DigiByte daemon binary and configuration to a connected Android device.
# Usage: ./scripts/push-node.sh [BINARY_PATH] [CONFIG_PATH]
# The script relies on device-select.sh to determine which device to target.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BINARY_PATH=${1:-"$REPO_ROOT/android/build/arm64-v8a/digibyted"}
CONFIG_PATH=${2:-"$REPO_ROOT/config/digimobile-pruned.conf"}
TARGET_DIR="/data/local/tmp/digimobile"

source "$SCRIPT_DIR/device-select.sh" "${DEVICE_ID:-}" >/dev/null

if [[ ! -f "$BINARY_PATH" ]]; then
  echo "Binary not found at $BINARY_PATH. Build the Android binary first." >&2
  exit 1
fi

if [[ ! -f "$CONFIG_PATH" ]]; then
  echo "Config not found at $CONFIG_PATH. Please provide a valid config file." >&2
  exit 1
fi

ADB=(adb -s "$SELECTED_DEVICE")

# Create target directories on the device.
"${ADB[@]}" shell "mkdir -p $TARGET_DIR/logs $TARGET_DIR/data"

# Push binary and config.
"${ADB[@]}" push "$BINARY_PATH" "$TARGET_DIR/digibyted"
"${ADB[@]}" push "$CONFIG_PATH" "$TARGET_DIR/digimobile.conf"

# Ensure executable permissions.
"${ADB[@]}" shell "chmod +x $TARGET_DIR/digibyted"

cat <<INFO
Pushed assets to device $SELECTED_DEVICE:
  Binary: $TARGET_DIR/digibyted
  Config: $TARGET_DIR/digimobile.conf
  Data dir: $TARGET_DIR/data
  Logs: $TARGET_DIR/logs
INFO
