#!/usr/bin/env bash
# Stop the DigiByte daemon on the selected Android device.
# Environment: requires adb on PATH and assumes the daemon was started via
# run-node.sh. Uses best-effort process discovery and kill; RPC shutdown can be
# added later when enabled.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="/data/local/tmp/digimobile"

log() { echo "[Digi-Mobile] $*"; }

die() {
  echo "[Digi-Mobile] ERROR: $*" >&2
  exit 1
}

command -v adb >/dev/null 2>&1 || die "adb is required to stop the daemon"
source "$SCRIPT_DIR/device-select.sh" "${DEVICE_ID:-}" >/dev/null
ADB=(adb -s "$SELECTED_DEVICE")

PID=$("${ADB[@]}" shell "pidof digibyted" 2>/dev/null | tr -d '\r') || true

if [[ -z "$PID" ]]; then
  PID=$("${ADB[@]}" shell "ps -A | grep digibyted | awk '{print \$2}'" | tr -d '\r' | head -n1)
fi

if [[ -z "$PID" ]]; then
  log "No digibyted process found on device $SELECTED_DEVICE."
  exit 0
fi

log "Stopping digibyted (pid: $PID) on device $SELECTED_DEVICE"
"${ADB[@]}" shell "kill $PID" || true

echo "[Digi-Mobile] Sent kill signal. If RPC is enabled, consider using 'digibyte-cli stop' instead."
