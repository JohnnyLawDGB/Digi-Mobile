#!/usr/bin/env bash
# Launch the DigiByte daemon on the selected Android device.
# Environment: requires adb on PATH, a device selected via device-select.sh, and
# assets pushed by push-node.sh. Designed for iterative testing; uses nohup and
# best-effort process tracking.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="/data/local/tmp/digimobile"
BINARY_PATH="$TARGET_DIR/digibyted"
CONFIG_PATH="$TARGET_DIR/digimobile.conf"
LOG_FILE="$TARGET_DIR/logs/digibyted-$(date +%Y%m%d-%H%M%S).log"

log() { echo "[Digi-Mobile] $*"; }

die() {
  echo "[Digi-Mobile] ERROR: $*" >&2
  exit 1
}

command -v adb >/dev/null 2>&1 || die "adb is required to run the daemon"
source "$SCRIPT_DIR/device-select.sh" "${DEVICE_ID:-}" >/dev/null
ADB=(adb -s "$SELECTED_DEVICE")

log "Ensuring target directories exist on device $SELECTED_DEVICE"
"${ADB[@]}" shell "mkdir -p $TARGET_DIR/logs $TARGET_DIR/data"

if ! "${ADB[@]}" shell "test -x $BINARY_PATH" >/dev/null; then
  die "Binary missing or not executable at $BINARY_PATH. Run push-node.sh first."
fi
if ! "${ADB[@]}" shell "test -f $CONFIG_PATH" >/dev/null; then
  die "Config missing at $CONFIG_PATH. Run push-node.sh or adjust path."
fi

START_CMD="cd $TARGET_DIR && nohup $BINARY_PATH -conf=$CONFIG_PATH -datadir=$TARGET_DIR/data > $LOG_FILE 2>&1 & echo \$!"

log "Starting daemon on device $SELECTED_DEVICE"
log "Command: $START_CMD"
PID=$("${ADB[@]}" shell "$START_CMD" | tr -d '\r')

cat <<INFO
[Digi-Mobile] Daemon started on device $SELECTED_DEVICE (pid: $PID)
Log file on device: $LOG_FILE
Tail logs: adb -s $SELECTED_DEVICE shell "tail -f $LOG_FILE"
INFO
