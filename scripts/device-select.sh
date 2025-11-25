#!/usr/bin/env bash
# Select an Android device/emulator ID using adb.
# This script is intended to be sourced by other scripts so they can reuse
# the SELECTED_DEVICE variable. It can also be run directly to print the
# selected device ID.

set -euo pipefail

# Usage: source ./scripts/device-select.sh [DEVICE_ID]
# - If DEVICE_ID is provided, it will be validated against connected devices.
# - If not provided, the first available device is chosen.
# The selected device ID is stored in SELECTED_DEVICE and echoed for convenience.

# shellcheck disable=SC2128
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ARG_DEVICE_ID=${1:-""}

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found on PATH. Please install Android platform-tools." >&2
  exit 1
fi

mapfile -t DEVICES < <(adb devices | awk '/\tdevice$/ {print $1}')

if [[ ${#DEVICES[@]} -eq 0 ]]; then
  echo "No connected devices/emulators detected. Connect one and run 'adb devices'." >&2
  exit 1
fi

if [[ -n "$ARG_DEVICE_ID" ]]; then
  if printf '%s\n' "${DEVICES[@]}" | grep -qx "$ARG_DEVICE_ID"; then
    SELECTED_DEVICE="$ARG_DEVICE_ID"
  else
    echo "Requested device '$ARG_DEVICE_ID' is not among connected devices." >&2
    printf 'Connected devices:\n%s\n' "${DEVICES[*]}" >&2
    exit 1
  fi
else
  SELECTED_DEVICE="${DEVICES[0]}"
  echo "No DEVICE_ID provided. Defaulting to first device: $SELECTED_DEVICE" >&2
  if [[ ${#DEVICES[@]} -gt 1 ]]; then
    echo "Other devices detected: ${DEVICES[*]}" >&2
    echo "To target a specific device, pass its ID as an argument: source device-select.sh <device_id>" >&2
  fi
fi

export SELECTED_DEVICE

echo "$SELECTED_DEVICE"

# If executed directly, exit successfully. When sourced, the caller will continue.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  exit 0
fi
