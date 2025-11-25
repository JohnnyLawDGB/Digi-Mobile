#!/usr/bin/env bash
# Pull DigiByte daemon logs from the selected Android device to the host.
# Usage: ./scripts/pull-logs.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET_DIR="/data/local/tmp/digimobile"

source "$SCRIPT_DIR/device-select.sh" "${DEVICE_ID:-}" >/dev/null

ADB=(adb -s "$SELECTED_DEVICE")
LOCAL_LOG_DIR="$REPO_ROOT/logs/device/$SELECTED_DEVICE"

mkdir -p "$LOCAL_LOG_DIR"

echo "Pulling logs from device $SELECTED_DEVICE..."
"${ADB[@]}" pull "$TARGET_DIR/logs" "$LOCAL_LOG_DIR"

echo "Logs copied to $LOCAL_LOG_DIR/logs"
