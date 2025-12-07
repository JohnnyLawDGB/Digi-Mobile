#!/usr/bin/env bash
# Verify that built binaries are the correct architecture (ARM64-only).
# Use this to diagnose "wrong architecture" build issues.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

log() {
  echo "[verify-build-abi] $*"
}

check_file_abi() {
  local filepath="$1"

  if [[ ! -f "$filepath" ]]; then
    log "MISSING: $filepath"
    return 1
  fi

  local file_type
  file_type="$(file "$filepath" 2>/dev/null || echo "unknown")"

  log "$filepath"
  log "  File type: $file_type"

  if echo "$file_type" | grep -q "aarch64"; then
    log "  ✓ Correct: arm64-v8a (aarch64)"
    return 0
  elif echo "$file_type" | grep -qE "(x86_64|Intel 80386|i386)"; then
    log "  ✗ ERROR: Built for x86/x86_64 instead of arm64!"
    return 1
  else
    log "  ✗ ERROR: Unknown or incorrect architecture"
    return 1
  fi
}

ANDROID_ABI="arm64-v8a"
log "Verifying build artifacts for ABI: $ANDROID_ABI"
log ""

files_to_check=(
  "${ROOT_DIR}/android/app/src/main/assets/bin/digibyted-arm64"
  "${ROOT_DIR}/android/app/src/main/assets/bin/digibyte-cli-arm64"
  "${ROOT_DIR}/android/app/src/main/jniLibs/${ANDROID_ABI}/digibyted"
  "${ROOT_DIR}/android/app/src/main/jniLibs/${ANDROID_ABI}/libdigimobile_jni.so"
)

errors=0
for f in "${files_to_check[@]}"; do
  if ! check_file_abi "$f" "$ANDROID_ABI"; then
    ((errors++)) || true
  fi
done

log ""
if [[ $errors -eq 0 ]]; then
  log "✓ All checked files have correct architecture."
  exit 0
else
  log "✗ $errors file(s) have incorrect or missing architecture."
  log ""
  log "To fix, run:"
  log "  ./scripts/clean-build.sh"
  log "  ABI=$ANDROID_ABI ANDROID_ABI=$ANDROID_ABI ./scripts/build-android.sh"
  exit 1
fi
