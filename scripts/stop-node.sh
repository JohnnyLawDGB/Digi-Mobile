#!/usr/bin/env bash
# Stop the DigiByte daemon on the selected Android device.
# Usage: ./scripts/stop-node.sh
# Attempts a best-effort stop by killing the digibyted process on device.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="/data/local/tmp/digimobile"

source "$SCRIPT_DIR/device-select.sh" "${DEVICE_ID:-}" >/dev/null

ADB=(adb -s "$SELECTED_DEVICE")

# Attempt to find the process via pidof first.
PID=$("${ADB[@]}" shell "pidof digibyted" 2>/dev/null | tr -d '\r') || true

if [[ -z "$PID" ]]; then
  # Fallback: parse ps output. This may vary by Android version.
  PID=$("${ADB[@]}" shell "ps -A | grep digibyted | awk '{print \$2}'" | tr -d '\r' | head -n1)
fi

if [[ -z "$PID" ]]; then
  echo "No digibyted process found on device $SELECTED_DEVICE."
  exit 0
fi

echo "Stopping digibyted (pid: $PID) on device $SELECTED_DEVICE"
"${ADB[@]}" shell "kill $PID" || true

echo "Sent kill signal. If RPC is enabled, consider using 'digibyte-cli stop' instead (TODO)."
