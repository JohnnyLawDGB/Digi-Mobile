#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[0;33m"
RESET="\033[0m"

color_echo() {
  local color="$1"
  shift
  local code="$RESET"
  case "$color" in
    green) code="$GREEN" ;;
    red) code="$RED" ;;
    yellow) code="$YELLOW" ;;
  esac
  printf "%b%s%b\n" "$code" "$*" "$RESET"
}

confirm() {
  local prompt="$1"
  local default="${2:-y}"
  local hint="[y/N]"
  if [[ "$default" =~ ^[Yy]$ ]]; then
    hint="[Y/n]"
  fi
  read -r -p "$prompt $hint " reply || true
  reply=${reply:-$default}
  [[ "$reply" =~ ^[Yy]$ ]]
}

detect_ndk() {
  if [[ -n "${ANDROID_NDK_HOME:-}" && -d "$ANDROID_NDK_HOME" ]]; then
    echo "$ANDROID_NDK_HOME"
    return 0
  fi
  if [[ -n "${ANDROID_NDK_ROOT:-}" && -d "$ANDROID_NDK_ROOT" ]]; then
    echo "$ANDROID_NDK_ROOT"
    return 0
  fi
  if [[ -n "${ANDROID_NDK:-}" && -d "$ANDROID_NDK" ]]; then
    echo "$ANDROID_NDK"
    return 0
  fi

  local candidates=(
    "$HOME/Android/Sdk/ndk"
    "$HOME/Library/Android/sdk/ndk"
    "/usr/local/share/android-ndk"
    "/usr/local/lib/android/sdk/ndk"
    "/usr/lib/android-sdk/ndk"
    "/usr/lib/android-sdk/ndk-bundle"
    "/opt/android-ndk"
    "/opt/homebrew/share/android-ndk"
  )

  for base in "${candidates[@]}"; do
    if [[ -d "$base" ]]; then
      if compgen -G "$base/*" > /dev/null; then
        local latest
        latest=$(ls -1d "$base"/* 2>/dev/null | sort | tail -n1)
        if [[ -d "$latest" ]]; then
          echo "$latest"
          return 0
        fi
      elif [[ -f "$base/source.properties" || -d "$base/toolchains" ]]; then
        echo "$base"
        return 0
      fi
    fi
  done
  return 1
}

detect_device() {
  command -v adb >/dev/null 2>&1 || { color_echo red "adb not found. Install Android platform-tools."; return 1; }
  mapfile -t devices < <(adb devices | awk '/\tdevice$/ {print $1}')
  if [[ ${#devices[@]} -eq 0 ]]; then
    color_echo red "No connected Android devices or emulators detected. Run 'adb devices' after enabling USB debugging."
    return 1
  fi
  local requested="${DEVICE_ID:-}"
  if [[ -n "$requested" ]]; then
    for d in "${devices[@]}"; do
      if [[ "$d" == "$requested" ]]; then
        echo "$requested"
        return 0
      fi
    done
    color_echo red "Requested device '$requested' is not connected. Connected: ${devices[*]}"
    return 1
  fi
  echo "${devices[0]}"
}

detect_prebuilt() {
  local abi="${ABI:-${ARCH:-arm64-v8a}}"
  local candidates=(
    "$REPO_ROOT/android/build/android-prefix/$abi/bin/digibyted"
    "$REPO_ROOT/android/output/digibyted"
    "$REPO_ROOT/android/dist/digibyted"
    "$REPO_ROOT/android/build/$abi/digibyted"
  )
  for path in "${candidates[@]}"; do
    if [[ -f "$path" ]]; then
      echo "$path"
      return 0
    fi
  done
  return 1
}
