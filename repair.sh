#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/scripts/common.sh"

TARGET_DIR="/data/local/tmp/digimobile"
DEFAULT_CONFIG="$SCRIPT_DIR/config/digimobile-pruned.conf"

DEVICE_ID=$(detect_device)
ADB=(adb -s "$DEVICE_ID")

color_echo yellow "Checking Digi-Mobile installation on $DEVICE_ID..."
"${ADB[@]}" shell "mkdir -p $TARGET_DIR/logs $TARGET_DIR/data" >/dev/null 2>&1 || true

declare -a repaired

PID=$("${ADB[@]}" shell "pidof digibyted" 2>/dev/null | tr -d '\r' || true)
if [[ -n "$PID" ]]; then
  color_echo yellow "Stopping existing digibyted process ($PID)"
  "${ADB[@]}" shell "kill $PID" || true
fi

binary_on_device=$("${ADB[@]}" shell "test -x $TARGET_DIR/digibyted" 2>/dev/null && echo yes || true)
config_on_device=$("${ADB[@]}" shell "test -f $TARGET_DIR/digimobile.conf" 2>/dev/null && echo yes || true)

local_binary=$(detect_prebuilt || true)
if [[ -z "$local_binary" ]]; then
  ndk_path=$(detect_ndk || true)
  if [[ -n "$ndk_path" ]] && confirm "Local binary missing. Build from source now?" "y"; then
    color_echo yellow "Building digibyted using detected NDK at $ndk_path..."
    ANDROID_NDK_HOME="$ndk_path" "$SCRIPT_DIR/scripts/build-android.sh"
    local_binary=$(detect_prebuilt || true)
  fi
fi

if [[ -z "$local_binary" ]]; then
  color_echo red "No local digibyted binary found. Run ./setup.sh to rebuild before repairing."
  exit 1
fi

if [[ -z "$binary_on_device" ]]; then
  color_echo yellow "Re-pushing binary to device..."
  "${ADB[@]}" push "$local_binary" "$TARGET_DIR/digibyted" >/dev/null
  "${ADB[@]}" shell "chmod +x $TARGET_DIR/digibyted" || true
  repaired+=("binary")
fi

if [[ -z "$config_on_device" ]]; then
  color_echo yellow "Re-pushing configuration to device..."
  "${ADB[@]}" push "$DEFAULT_CONFIG" "$TARGET_DIR/digimobile.conf" >/dev/null
  repaired+=("config")
fi

if [[ ${#repaired[@]} -eq 0 ]]; then
  color_echo green "Nothing was missing; ensuring services are running."
else
  color_echo green "Repaired: ${repaired[*]}"
fi

color_echo yellow "Restarting digibyted on $DEVICE_ID..."
DEVICE_ID="$DEVICE_ID" "$SCRIPT_DIR/scripts/run-node.sh"
color_echo green "Repair complete."
