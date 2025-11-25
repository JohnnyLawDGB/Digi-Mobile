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

DEVICE_ID=$(detect_device)
color_echo green "Using device: $DEVICE_ID"

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

CONFIG_PATH="$DEFAULT_PRUNED_CONFIG"
if confirm "Use pruned config (recommended)?" "y"; then
  color_echo green "Selected pruned config: $CONFIG_PATH"
else
  CONFIG_PATH="$DEV_CONFIG"
  color_echo yellow "Selected dev config: $CONFIG_PATH"
fi

prebuilt="$(detect_prebuilt || true)"
use_prebuilt=false
if [[ -n "$prebuilt" ]]; then
  color_echo green "Found prebuilt binary at $prebuilt"
  if confirm "Use the prebuilt binary instead of building?" "y"; then
    use_prebuilt=true
  fi
fi

if [[ "$use_prebuilt" == false ]]; then
  print_step "Build selection"
  if [[ -z "$NDK_PATH" ]]; then
    color_echo red "Android NDK not found. Set ANDROID_NDK_HOME or install the NDK before building."
    exit 1
  fi
  color_echo yellow "Building DigiByte Core for Android (this may take a while)..."
  ANDROID_NDK_HOME="$NDK_PATH" "$REPO_ROOT/scripts/build-android.sh"
  prebuilt="$(detect_prebuilt || true)"
fi

if [[ -z "$prebuilt" ]]; then
  color_echo red "No digibyted binary found. Please ensure the build completed successfully."
  exit 1
fi

print_step "Pushing binary and config to device"
DEVICE_ID="$DEVICE_ID" "$REPO_ROOT/scripts/push-node.sh" "$prebuilt" "$CONFIG_PATH"

print_step "Starting the node"
DEVICE_ID="$DEVICE_ID" "$REPO_ROOT/scripts/run-node.sh"

print_checklist
