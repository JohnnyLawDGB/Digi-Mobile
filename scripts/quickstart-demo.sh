#!/usr/bin/env bash
# One-shot demo helper to build, push, and run Digi-Mobile on a connected device.
# For hobby/testing use only. Assumes required Android build deps and adb are set up.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_DIR="/data/local/tmp/digimobile"
DEFAULT_CONFIG="$REPO_ROOT/config/digimobile-pruned.conf"

current_step=""

banner() {
  cat <<'BANNER'
============================================================
        Digi-Mobile Quickstart Demo (Testing/Hobby)
============================================================
- Testing / hobby use only
- Do NOT use this for serious amounts of money
- No guarantees; proceed at your own risk
============================================================
BANNER
}

tell_plan() {
  cat <<INFO
This script will:
  1) Initialize the DigiByte Core submodule and dependencies (scripts/setup-core.sh)
  2) Build Android artifacts (scripts/build-android.sh)
  3) Push the binary and default config to a connected device (scripts/push-node.sh)
  4) Start the DigiByte daemon on that device (scripts/run-node.sh)

Assumptions:
  - ANDROID_NDK_HOME (or equivalent) is set as required by build-android.sh
  - adb detects the target device (try: adb devices)
  - core/ has not been modified in incompatible ways
  - You are okay with experimental software and potential data loss
INFO
}

on_error() {
  echo "[Digi-Mobile] ERROR during step: ${current_step:-unknown}" >&2
  echo "[Digi-Mobile] The demo stopped. Check the output above, fix the issue, and re-run." >&2
}

run_step() {
  current_step="$1"
  shift
  echo "[Digi-Mobile] Running: ${current_step}"
  "$@"
  echo "[Digi-Mobile] Completed: ${current_step}"
  echo
}

trap on_error ERR
banner
tell_plan

echo "[Digi-Mobile] Starting demo in $REPO_ROOT"

run_step "Setup core" "${SCRIPT_DIR}/setup-core.sh"
run_step "Build Android artifacts" "${SCRIPT_DIR}/build-android.sh"
run_step "Push binary and config" "${SCRIPT_DIR}/push-node.sh" "$REPO_ROOT/android/build/arm64-v8a/digibyted" "$DEFAULT_CONFIG"
run_step "Run node on device" "${SCRIPT_DIR}/run-node.sh"

cat <<SUMMARY
============================================================
Digi-Mobile demo completed.
- Node files live on the device at: ${TARGET_DIR}
- Config pushed from: ${DEFAULT_CONFIG} (stored as ${TARGET_DIR}/digimobile.conf)

Docs to read next:
  - docs/GETTING-STARTED-NONTECH.md
  - docs/RUNNING-ON-DEVICE.md
  - docs/CONFIGURATION.md
============================================================
SUMMARY
