#!/usr/bin/env bash
# Pull DigiByte daemon logs from the selected Android device to the host.
# Environment: requires adb on PATH and a running/stopped digibyted that writes
# to /data/local/tmp/digimobile/logs. Suitable for quick debugging.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET_DIR="/data/local/tmp/digimobile"

log() { echo "[Digi-Mobile] $*"; }

die() {
  echo "[Digi-Mobile] ERROR: $*" >&2
  exit 1
}

command -v adb >/dev/null 2>&1 || die "adb is required to pull logs"
source "$SCRIPT_DIR/device-select.sh" "${DEVICE_ID:-}" >/dev/null
ADB=(adb -s "$SELECTED_DEVICE")
LOCAL_LOG_DIR="$REPO_ROOT/logs/device/$SELECTED_DEVICE"

mkdir -p "$LOCAL_LOG_DIR"

log "Pulling logs from device $SELECTED_DEVICE..."
if ! "${ADB[@]}" pull "$TARGET_DIR/logs" "$LOCAL_LOG_DIR"; then
  die "Failed to pull logs. Ensure the daemon has written logs under $TARGET_DIR/logs."
fi

log "Logs copied to $LOCAL_LOG_DIR/logs"
