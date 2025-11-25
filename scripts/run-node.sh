#!/usr/bin/env bash
# Launch the DigiByte daemon on the selected Android device.
# Usage: ./scripts/run-node.sh
# Assumes assets are already pushed via push-node.sh.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="/data/local/tmp/digimobile"
BINARY_PATH="$TARGET_DIR/digibyted"
CONFIG_PATH="$TARGET_DIR/digimobile.conf"
LOG_FILE="$TARGET_DIR/logs/digibyted-$(date +%Y%m%d-%H%M%S).log"

source "$SCRIPT_DIR/device-select.sh" "${DEVICE_ID:-}" >/dev/null

ADB=(adb -s "$SELECTED_DEVICE")

# Ensure directories exist on device.
"${ADB[@]}" shell "mkdir -p $TARGET_DIR/logs $TARGET_DIR/data"

START_CMD="cd $TARGET_DIR && nohup $BINARY_PATH -conf=$CONFIG_PATH -datadir=$TARGET_DIR/data > $LOG_FILE 2>&1 & echo \$!"

echo "Starting daemon on device $SELECTED_DEVICE with command:"
echo "  $START_CMD"

PID=$("${ADB[@]}" shell "$START_CMD" | tr -d '\r')

cat <<INFO
Daemon started on device $SELECTED_DEVICE (pid: $PID)
Log file on device: $LOG_FILE
Tail logs: adb -s $SELECTED_DEVICE shell "tail -f $LOG_FILE"
INFO
