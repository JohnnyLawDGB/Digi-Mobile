#!/usr/bin/env bash
set -euo pipefail

# Cross-compile DigiByte Core for Android. Example usage:
#   ARCH=arm64-v8a API=29 ANDROID_NDK_ROOT=/path/to/ndk ./scripts/build-android.sh
# Supports ARCH values: arm64-v8a, armeabi-v7a

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CORE_DIR="$ROOT_DIR/core"
TOOLCHAIN_DIR="$ROOT_DIR/android/toolchain"
OUTPUT_DIR="$ROOT_DIR/android/output"
API=${API:-29}
ARCH=${ARCH:-arm64-v8a}

if [[ -z "${ANDROID_NDK_ROOT:-}" ]]; then
  echo "ANDROID_NDK_ROOT is not set." >&2
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
export AR=${AR:-llvm-ar}
export CC=${CC:-"${HOST_TRIPLE}${API}-clang"}
export CXX=${CXX:-"${HOST_TRIPLE}${API}-clang++"}
export RANLIB=${RANLIB:-llvm-ranlib}
export LD=${LD:-ld.lld}
export CPPFLAGS="${CPPFLAGS:-} -fPIC"
export CXXFLAGS="${CXXFLAGS:-} -Os -ffunction-sections -fdata-sections"
export LDFLAGS="${LDFLAGS:-} -Wl,--gc-sections"

cd "$CORE_DIR"

if [[ ! -f ./configure ]]; then
  echo "Running autogen..."
  ./autogen.sh
fi

SYSROOT="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot"

./configure \
  --host="$HOST_TRIPLE" \
  --prefix="${OUTPUT_DIR}/${ARCH}" \
  --with-sysroot="$SYSROOT" \
  --disable-bench \
  --disable-man \
  --disable-tests \
  --disable-gui-tests \
  --without-gui \
  --with-miniupnpc=no \
  --disable-zmq \
  --enable-reduce-exports

make -j"${NPROC:-$(nproc)}"
make install

echo "Artifacts staged under ${OUTPUT_DIR}/${ARCH}"
