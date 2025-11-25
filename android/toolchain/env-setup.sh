#!/usr/bin/env bash
# Helper to export common Android NDK toolchain variables for Digi-Mobile builds.
# Usage: source android/toolchain/env-setup.sh <arch> <api> <ndk_root>

set -euo pipefail

ARCH=${1:-arm64-v8a}
API=${2:-29}
ANDROID_NDK_ROOT=${3:-${ANDROID_NDK_ROOT:-}}

if [[ -z "$ANDROID_NDK_ROOT" ]]; then
  echo "ANDROID_NDK_ROOT is required" >&2
  exit 1
fi

case "$ARCH" in
  arm64-v8a)
    HOST_TRIPLE=aarch64-linux-android
    ;;
  armeabi-v7a)
    HOST_TRIPLE=arm-linux-androideabi
    ;;
  *)
    echo "Unsupported ARCH: $ARCH" >&2
    exit 1
    ;;
esac

export PATH="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
export AR=llvm-ar
export AS=llvm-as
export CC="${HOST_TRIPLE}${API}-clang"
export CXX="${HOST_TRIPLE}${API}-clang++"
export LD=ld.lld
export RANLIB=llvm-ranlib
export STRIP=llvm-strip
export SYSROOT="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot"

echo "Configured toolchain for $ARCH (API $API)"
