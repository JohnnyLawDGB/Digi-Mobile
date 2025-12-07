#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"
source "$REPO_ROOT/scripts/common.sh"

TARGET_DIR="/data/local/tmp/digimobile"
DEFAULT_PRUNED_CONFIG="$REPO_ROOT/config/digimobile-pruned.conf"
DEV_CONFIG="$REPO_ROOT/config/digimobile-dev.conf"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { color_echo red "Missing required command: $1"; exit 1; }
}

print_banner() {
  echo ""
  color_echo yellow "============================================================"
  color_echo yellow "   Digi-Mobile – TESTING/HOBBY USE ONLY"
  color_echo yellow "   This wizard sets up a pruned DigiByte node on Android."
  color_echo yellow "   Not for storing significant funds. Proceed with caution."
  color_echo yellow "============================================================"
  echo ""
}

print_checklist() {
  echo ""
  color_echo green "Setup complete!"
  echo ""
  color_echo yellow "Next steps:"
  echo "  • Stop the node: ./stop.sh"
  echo "  • Check status: ./status.sh"
  echo "  • Repair if needed: ./repair.sh"
  echo "  • Read the beginner guide: docs/GETTING-STARTED-NONTECH.md"
  echo ""
  color_echo green "Checklist:"
  echo "  [✓] Binary on device"
  echo "  [✓] Config on device"
  echo "  [✓] Node started"
}

print_step() {
  color_echo yellow "==> $*"
}

print_banner

require_cmd git
require_cmd adb

verify_arm64_binary() {
  local path="$1"
  [[ -f "$path" ]] || return 1
  local desc
  desc="$(file "$path" || true)"
  if [[ ! "$desc" =~ aarch64 ]]; then
    color_echo red "[setup] ERROR: Non-ARM64 binary detected at $path (file: $desc)"
    return 1
  fi
  return 0
}

DEVICE_ID=$(detect_device)
color_echo green "Using device: $DEVICE_ID"

detect_device_abi() {
  local device_id="$1"
  local raw_abies
  raw_abies=$(adb -s "$device_id" shell getprop ro.product.cpu.abilist 2>/dev/null | tr -d '\r') || raw_abies=""
  if [[ -z "$raw_abies" ]]; then
    raw_abies=$(adb -s "$device_id" shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r') || raw_abies=""
  fi

  if [[ -z "$raw_abies" ]]; then
    color_echo red "[setup] ERROR: Unable to detect device ABI via adb getprop"
    exit 1
  fi

  IFS=',' read -r -a device_abis <<< "$raw_abies"

  local selected=""
  for abi in "${device_abis[@]}"; do
    if [[ "$abi" == "arm64-v8a" ]]; then
      selected="$abi"
      break
    fi
  done

  if [[ -z "$selected" ]]; then
    color_echo red "[setup] ERROR: Device ABI(s) '$raw_abies' not supported (need arm64-v8a)"
    exit 1
  fi

  echo "$raw_abies|$selected"
}

abi_detection=$(detect_device_abi "$DEVICE_ID")
DEVICE_ABIS_RAW="${abi_detection%%|*}"
SELECTED_ABI="${abi_detection##*|}"
color_echo green "[Digi-Mobile] Detected device ABI(s): $DEVICE_ABIS_RAW -> using $SELECTED_ABI"
export ABI="$SELECTED_ABI"
export ANDROID_ABI="$SELECTED_ABI"
color_echo yellow "[Digi-Mobile] Using ABI $SELECTED_ABI for device $DEVICE_ID"

if [[ "$SELECTED_ABI" != "arm64-v8a" ]]; then
  color_echo red "[setup] ERROR: Android pipeline is ARM64-only. Detected ABI $SELECTED_ABI."
  exit 1
fi

NDK_PATH=$(detect_ndk || true)
if [[ -n "$NDK_PATH" ]]; then
  export ANDROID_NDK_HOME="$NDK_PATH"
  color_echo green "Detected Android NDK at $NDK_PATH"
else
  color_echo yellow "Android NDK not auto-detected. Building from source will require ANDROID_NDK_HOME to be set."
fi

if [[ ! -d "$REPO_ROOT/core/.git" ]]; then
  print_step "Initializing DigiByte Core submodule"
  "$REPO_ROOT/scripts/fetch-core.sh"
else
  color_echo green "core/ submodule already initialized."
fi

print_step "Syncing DigiByte Core checkout (fetch + depends tree)"
"$REPO_ROOT/scripts/setup-core.sh"

CONFIG_PATH="$DEFAULT_PRUNED_CONFIG"
if confirm "Use pruned config (recommended)?" "y"; then
  color_echo green "Selected pruned config: $CONFIG_PATH"
else
  CONFIG_PATH="$DEV_CONFIG"
  color_echo yellow "Selected dev config: $CONFIG_PATH"
fi

prebuilt="$(detect_prebuilt || true)"
if [[ -n "$prebuilt" ]]; then
  if verify_arm64_binary "$prebuilt"; then
    color_echo green "[Digi-Mobile] Using existing ARM64 binary: $prebuilt"
  else
    color_echo yellow "[Digi-Mobile] Ignoring non-ARM64 prebuilt at $prebuilt"
    prebuilt=""
  fi
fi

if [[ -z "$prebuilt" ]]; then
  print_step "Build selection"
  if [[ -z "$NDK_PATH" ]]; then
    color_echo red "Android NDK not found. Set ANDROID_NDK_HOME or install the NDK before building."
    exit 1
  fi
  color_echo yellow "Building DigiByte Core for Android ($SELECTED_ABI, this may take a while)..."
  # Explicitly pass ABI and NDK to ensure correct architecture is built
  ABI="$SELECTED_ABI" ANDROID_ABI="$SELECTED_ABI" ANDROID_NDK_HOME="$NDK_PATH" "$REPO_ROOT/scripts/build-android.sh"
  prebuilt="$(detect_prebuilt || true)"
fi

if [[ -z "$prebuilt" ]]; then
  color_echo red "No digibyted binary found. Please ensure the build completed successfully."
  exit 1
fi

if ! verify_arm64_binary "$prebuilt"; then
  color_echo red "[setup] ERROR: Non-ARM64 binary located at $prebuilt after build."
  exit 1
fi

print_step "Pushing binary and config to device"
DEVICE_ID="$DEVICE_ID" "$REPO_ROOT/scripts/push-node.sh" "$prebuilt" "$CONFIG_PATH"

print_step "Starting the node"
DEVICE_ID="$DEVICE_ID" "$REPO_ROOT/scripts/run-node.sh"

print_checklist
