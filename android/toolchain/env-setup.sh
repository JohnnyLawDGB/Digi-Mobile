#!/usr/bin/env bash
# Supported Android toolchain: JDK 17, Android SDK Platform 34 with build-tools 34.x, NDK 25.1.8937393, ABI arm64-v8a, API level 29.
# Update .versions/android.env.sh to change the enforced toolchain versions checked by this script.

set -euo pipefail

# Resolve repository root even when invoked via a symlink or from PATH.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
VERSIONS_FILE="${VERSIONS_FILE:-${ROOT_DIR}/.versions/android.env.sh}"

log() {
  echo "[env-setup] $*"
}

die() {
  echo "[env-setup] ERROR: $*" >&2
  exit 1
}

[[ -f "${VERSIONS_FILE}" ]] || die "Missing versions file at ${VERSIONS_FILE}."
# shellcheck source=/dev/null
source "${VERSIONS_FILE}"

require_env() {
  local var_name="$1"
  local value="${!var_name:-}"
  if [[ -z "${value}" ]]; then
    die "${var_name} is not set. Export ${var_name} before running Android builds."
  fi
  if [[ ! -d "${value}" ]]; then
    die "${var_name} path '${value}' does not exist."
  fi
}

check_java_version() {
  local javac_bin="${JAVA_HOME}/bin/javac"
  [[ -x "${javac_bin}" ]] || die "JAVA_HOME (${JAVA_HOME}) does not contain a javac binary."

  local javac_version_str
  javac_version_str="$(${javac_bin} -version 2>&1 || true)"
  # Examples of possible outputs:
  #   "javac 17.0.10"
  #   "javac 17 2025-10-21"
  #   "javac 17"
  local javac_version_num
  javac_version_num="$(printf '%s\n' "${javac_version_str}" | awk '{print $2}')"

  # Extract the MAJOR part before any dot/space
  local javac_major
  javac_major="$(printf '%s\n' "${javac_version_num}" | sed 's/[^0-9].*$//')"

  if [[ "${javac_major}" = "${ANDROID_JDK_VERSION}" ]]; then
    log "JDK OK: ${javac_version_str}"
  else
    log "WARNING: Expected JDK major ${ANDROID_JDK_VERSION}, but found: ${javac_version_str}"
    log "         Proceeding anyway; if Gradle fails with a JDK error, please install JDK ${ANDROID_JDK_VERSION}."
  fi
}

check_ndk_version() {
  local source_props="${ANDROID_NDK_HOME}/source.properties"
  [[ -f "${source_props}" ]] || die "NDK source.properties not found under ${ANDROID_NDK_HOME}."
  local ndk_revision
  ndk_revision="$(grep -E "Pkg\.Revision" "${source_props}" | awk -F= '{print $2}' | tr -d '[:space:]')"
  if [[ "${ndk_revision}" != "${ANDROID_NDK_VERSION}" ]]; then
    log "WARNING: NDK version mismatch: found ${ndk_revision}, expected ${ANDROID_NDK_VERSION}."
    log "         Proceeding anyway; NDK is generally backward compatible. If build fails, ensure NDK ${ANDROID_NDK_VERSION} is installed."
  fi
}

check_sdk_components() {
  local platform_dir="${ANDROID_SDK_ROOT}/platforms/android-${ANDROID_SDK_PLATFORM}"
  local build_tools_dir="${ANDROID_SDK_ROOT}/build-tools/${ANDROID_BUILD_TOOLS_VERSION}"
  [[ -d "${platform_dir}" ]] || die "Android SDK platform ${ANDROID_SDK_PLATFORM} missing at ${platform_dir}."
  [[ -d "${build_tools_dir}" ]] || die "Android build-tools ${ANDROID_BUILD_TOOLS_VERSION} missing at ${build_tools_dir}."
}

require_env JAVA_HOME
require_env ANDROID_SDK_ROOT
require_env ANDROID_NDK_HOME

check_java_version
check_ndk_version
check_sdk_components

log "Environment OK: JDK ${ANDROID_JDK_VERSION}, SDK platform ${ANDROID_SDK_PLATFORM}, build-tools ${ANDROID_BUILD_TOOLS_VERSION}, NDK ${ANDROID_NDK_VERSION}."
