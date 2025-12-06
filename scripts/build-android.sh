#!/usr/bin/env bash
# Supported Android toolchain: JDK 17, Android SDK Platform 34 with build-tools 34.x, NDK 25.1.8937393, ABIs arm64-v8a/armeabi-v7a, API level 29.
# Update .versions/android.env.sh to change the enforced Android toolchain and CMake arguments.

# Orchestrate Digi-Mobile's Android build steps, including compiling DigiByte
# Core for Android arm64-v8a/armeabi-v7a and staging the daemon binary as an asset for the
# APK. This script focuses on wiring up artifacts; it does not alter consensus
# rules.
set -euo pipefail

# Resolve repository root even when invoked via a symlink or from PATH.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
VERSIONS_FILE="${ROOT_DIR}/.versions/android.env.sh"
ENV_SETUP_SCRIPT="${ROOT_DIR}/android/toolchain/env-setup.sh"
CMAKE_BUILD_ROOT="${ROOT_DIR}/android/build"
JNI_LIBS_DIR="${ROOT_DIR}/android/app/src/main/jniLibs"
CMAKE_GENERATOR="Ninja"
SUPPORTED_ABIS=("arm64-v8a" "armeabi-v7a")

log() {
  echo "[build-android] $*"
}

die() {
  echo "[build-android] ERROR: $*" >&2
  exit 1
}

[[ -f "${VERSIONS_FILE}" ]] || die "Missing ${VERSIONS_FILE}. Ensure the repository checkout includes the versioned Android toolchain files."
# shellcheck source=/dev/null
source "${VERSIONS_FILE}"
# shellcheck source=/dev/null
source "${ENV_SETUP_SCRIPT}"

build_digibyte_for_abi() {
  local ABI="$1"
  local CMAKE_BUILD_DIR="${CMAKE_BUILD_ROOT}/${ABI}"
  local ANDROID_PREFIX="${CMAKE_BUILD_DIR}/android-prefix/${ABI}"
  local BIN_DIR="${ANDROID_PREFIX}/bin"
  local LIB_DIR="${ANDROID_PREFIX}/lib"
  local JNI_TARGET_DIR="${JNI_LIBS_DIR}/${ABI}"
  local JNI_SO_SOURCE="${CMAKE_BUILD_DIR}/jni-lib/${ABI}/libdigimobile_jni.so"
  local ASSET_BIN_DIR="${ROOT_DIR}/android/app/src/main/assets/bin"

  : "${ANDROID_NDK_ROOT:=${ANDROID_NDK_HOME}}"
  TOOLCHAIN_BIN="${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin"
  ANDROID_API_LEVEL="${ANDROID_NDK_API_LEVEL}"

  case "${ABI}" in
    arm64-v8a)
      ANDROID_TRIPLE="aarch64-linux-android"
      ;;
    armeabi-v7a)
      ANDROID_TRIPLE="armv7a-linux-androideabi"
      ;;
    *)
      echo "[env-setup] ERROR: Unsupported ANDROID_ABI '${ABI}'" >&2
      exit 1
      ;;
  esac

  export CC="${TOOLCHAIN_BIN}/${ANDROID_TRIPLE}${ANDROID_API_LEVEL}-clang"
  export CXX="${TOOLCHAIN_BIN}/${ANDROID_TRIPLE}${ANDROID_API_LEVEL}-clang++"
  export AR="${TOOLCHAIN_BIN}/llvm-ar"
  export RANLIB="${TOOLCHAIN_BIN}/llvm-ranlib"
  export LD="${TOOLCHAIN_BIN}/ld.lld"

  log "Configuring DigiByte Core build via CMake (${ABI}, android-${ANDROID_NDK_API_LEVEL})"
  cmake -S "${ROOT_DIR}/android" -B "${CMAKE_BUILD_DIR}" \
    -G "${CMAKE_GENERATOR}" \
    -DANDROID_ABI="${ABI}" \
    -DANDROID_PLATFORM="android-${ANDROID_NDK_API_LEVEL}" \
    -DCMAKE_TOOLCHAIN_FILE="${ROOT_DIR}/android/toolchain-android.cmake"

  log "Building digibyted for Android (${ABI})"
  cmake --build "${CMAKE_BUILD_DIR}" --target digibyted

  log "Building JNI bridge (libdigimobile_jni.so)"
  cmake --build "${CMAKE_BUILD_DIR}" --target digimobile_jni

  [[ -d "${BIN_DIR}" ]] || die "digibyted binary missing at ${BIN_DIR}; CMake install step may have failed."
  [[ -f "${JNI_SO_SOURCE}" ]] || die "JNI shared library missing at ${JNI_SO_SOURCE}; JNI build may have failed."

  log "Staging native artifacts into ${JNI_TARGET_DIR}"
  mkdir -p "${JNI_TARGET_DIR}"
  shopt -s nullglob

  if [[ "${ABI}" == "arm64-v8a" ]]; then
    # For arm64-v8a: use the cross-compiled android-prefix path and stage
    # binaries with the names the Android app expects. Ensure executables have
    # the correct permissions (install -m 755).
    if [[ -f "${BIN_DIR}/digibyted" ]]; then
      install -m 755 "${BIN_DIR}/digibyted" "${JNI_TARGET_DIR}/digibyted"
    else
      die "Expected digibyted at ${BIN_DIR}/digibyted but it is missing"
    fi

    # Copy JNI .so
    install -m 644 "${JNI_SO_SOURCE}" "${JNI_TARGET_DIR}/$(basename "${JNI_SO_SOURCE}")"

    # Copy any library .so files if present
    if [[ -d "${LIB_DIR}" ]]; then
      for so in "${LIB_DIR}"/*.so; do
        install -m 644 "${so}" "${JNI_TARGET_DIR}/"
      done
    fi

    shopt -u nullglob

    # Print staged messages expected by the consumer
    echo "[env-setup] digibyted staged to ${JNI_TARGET_DIR}."

    # Stage into APK assets with new asset names (no -v8a suffix)
    log "Copying digibyted into APK assets at ${ASSET_BIN_DIR}"
    mkdir -p "${ASSET_BIN_DIR}"
    install -m 755 "${BIN_DIR}/digibyted" "${ASSET_BIN_DIR}/digibyted-arm64"
    echo "[env-setup] Copied digibyted-arm64 asset"

    if [[ -f "${BIN_DIR}/digibyte-cli" ]]; then
      install -m 755 "${BIN_DIR}/digibyte-cli" "${ASSET_BIN_DIR}/digibyte-cli-arm64"
      echo "[env-setup] Copied digibyte-cli-arm64 asset"
    else
      echo "[env-setup] digibyte-cli not built for ${ABI}; CLI-driven features will be unavailable in the APK"
    fi

  else
    # Default behavior for other ABIs: copy everything into jniLibs and preserve executability
    for binary in "${BIN_DIR}"/*; do
      cp "${binary}" "${JNI_TARGET_DIR}/"
      chmod 755 "${JNI_TARGET_DIR}/$(basename "${binary}")" || true
      log "Copied $(basename "${binary}")"
    done
    cp "${JNI_SO_SOURCE}" "${JNI_TARGET_DIR}/"
    log "Copied $(basename "${JNI_SO_SOURCE}")"
    if [[ -d "${LIB_DIR}" ]]; then
      for so in "${LIB_DIR}"/*.so; do
        cp "${so}" "${JNI_TARGET_DIR}/"
        log "Copied $(basename "${so}")"
      done
    fi
    shopt -u nullglob

    log "digibyted staged to ${JNI_TARGET_DIR}."
    log "Copying digibyted into APK assets at ${ASSET_BIN_DIR}"
    mkdir -p "${ASSET_BIN_DIR}"
    cp "${BIN_DIR}/digibyted" "${ASSET_BIN_DIR}/digibyted-${ABI}"
    log "Copied digibyted-${ABI} asset"
    if [[ -f "${BIN_DIR}/digibyte-cli" ]]; then
      cp "${BIN_DIR}/digibyte-cli" "${ASSET_BIN_DIR}/digibyte-cli-${ABI}"
      log "Copied digibyte-cli-${ABI} asset"
    else
      log "digibyte-cli not built for ${ABI}; CLI-driven features will be unavailable in the APK"
    fi
  fi
}

abis_to_build=()
if [[ -n "${ABI:-}" ]]; then
  for supported in "${SUPPORTED_ABIS[@]}"; do
    if [[ "$supported" == "$ABI" ]]; then
      abis_to_build=("$ABI")
      break
    fi
  done
  if [[ ${#abis_to_build[@]} -eq 0 ]]; then
    die "Unsupported ABI '$ABI'. Supported: ${SUPPORTED_ABIS[*]}"
  fi
  log "Building requested ABI: ${abis_to_build[*]}"
else
  abis_to_build=("${SUPPORTED_ABIS[@]}")
fi

for abi in "${abis_to_build[@]}"; do
  build_digibyte_for_abi "$abi"
done

log "Run ./gradlew assembleDebug (from android/; helper script forwards to repo wrapper) to package the APK with the bundled daemon."
log "APK outputs: android/app/build/outputs/apk/debug/app-debug.apk and android/app/build/outputs/apk/release/app-release.apk"
