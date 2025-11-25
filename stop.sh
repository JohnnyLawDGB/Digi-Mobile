#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/scripts/common.sh"

TARGET_DIR="/data/local/tmp/digimobile"

DEVICE_ID=$(detect_device)
ADB=(adb -s "$DEVICE_ID")

color_echo yellow "Stopping DigiByte node on $DEVICE_ID..."
PID=$("${ADB[@]}" shell "pidof digibyted" 2>/dev/null | tr -d '\r' || true)

if [[ -z "$PID" ]]; then
  PID=$("${ADB[@]}" shell "ps -A | grep digibyted | awk '{print \$2}'" 2>/dev/null | tr -d '\r' | head -n1)
fi

if [[ -z "$PID" ]]; then
  color_echo yellow "No running digibyted process found on $DEVICE_ID."
  exit 0
fi

"${ADB[@]}" shell "kill $PID" || true
color_echo green "Node stopped on $DEVICE_ID."
