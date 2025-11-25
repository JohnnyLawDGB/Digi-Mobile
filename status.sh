#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/scripts/common.sh"

TARGET_DIR="/data/local/tmp/digimobile"

DEVICE_ID=$(detect_device)
ADB=(adb -s "$DEVICE_ID")

PID=$("${ADB[@]}" shell "pidof digibyted" 2>/dev/null | tr -d '\r' || true)
if [[ -z "$PID" ]]; then
  PID=$("${ADB[@]}" shell "ps -A | grep digibyted | awk '{print \$2}'" 2>/dev/null | tr -d '\r' | head -n1)
fi

if [[ -n "$PID" ]]; then
  color_echo green "Status: Running (pid $PID) on $DEVICE_ID"
else
  color_echo red "Status: Not running on $DEVICE_ID"
fi

disk_usage=$("${ADB[@]}" shell "du -sh $TARGET_DIR/data" 2>/dev/null | tr -d '\r' || true)
if [[ -n "$disk_usage" ]]; then
  color_echo yellow "Data usage: $disk_usage"
else
  color_echo yellow "Data directory not found on device."
fi

latest_log=$("${ADB[@]}" shell "ls -t $TARGET_DIR/logs/*.log 2>/dev/null | head -n1" | tr -d '\r' || true)
if [[ -n "$latest_log" ]]; then
  color_echo yellow "Last 10 log lines from ${latest_log}:"
  "${ADB[@]}" shell "tail -n 10 $latest_log" || true
else
  color_echo yellow "No log files found yet."
fi
