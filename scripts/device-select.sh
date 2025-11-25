#!/usr/bin/env bash
# Select an Android device/emulator ID using adb.
# Environment: requires adb on PATH with at least one connected device. Intended
# to be sourced by other Digi-Mobile scripts to populate SELECTED_DEVICE.
# TODO: Make device selection non-interactive for CI via environment hints.
set -euo pipefail

log() { echo "[Digi-Mobile] $*"; }

die() {
  echo "[Digi-Mobile] ERROR: $*" >&2
  exit 1
}

# shellcheck disable=SC2128
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARG_DEVICE_ID=${1:-""}

command -v adb >/dev/null 2>&1 || die "adb not found on PATH. Install Android platform-tools."
mapfile -t DEVICES < <(adb devices | awk '/\tdevice$/ {print $1}')
[[ ${#DEVICES[@]} -gt 0 ]] || die "No connected devices/emulators detected. Run 'adb devices'."

if [[ -n "$ARG_DEVICE_ID" ]]; then
  if printf '%s\n' "${DEVICES[@]}" | grep -qx "$ARG_DEVICE_ID"; then
    SELECTED_DEVICE="$ARG_DEVICE_ID"
  else
    printf 'Connected devices:\n%s\n' "${DEVICES[*]}" >&2
    die "Requested device '$ARG_DEVICE_ID' is not among connected devices."
  fi
else
  SELECTED_DEVICE="${DEVICES[0]}"
  log "No DEVICE_ID provided. Defaulting to first device: $SELECTED_DEVICE"
  if [[ ${#DEVICES[@]} -gt 1 ]]; then
    log "Other devices detected: ${DEVICES[*]}"
  fi
fi

export SELECTED_DEVICE
echo "$SELECTED_DEVICE"

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  exit 0
fi
