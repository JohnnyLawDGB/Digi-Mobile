#!/usr/bin/env bash
# Supported Android toolchain: JDK 17, Android SDK Platform 34 with build-tools 34.x, NDK 25.1.8937393, ABI arm64-v8a, API level 29.
# Update .versions/android.env.sh to change the enforced toolchain versions checked by this script.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
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
  local java_bin="${JAVA_HOME}/bin/java"
  [[ -x "${java_bin}" ]] || die "JAVA_HOME (${JAVA_HOME}) does not contain a java binary."
  local reported_version
  reported_version="$(${java_bin} -version 2>&1 | head -n1 | sed -E 's/.*version "([0-9]+).*"/\1/')"
  if [[ "${reported_version}" != "${ANDROID_JDK_VERSION}" ]]; then
    die "JAVA_HOME points to JDK ${reported_version}, expected ${ANDROID_JDK_VERSION}."
  fi
}

check_ndk_version() {
  local source_props="${ANDROID_NDK_HOME}/source.properties"
  [[ -f "${source_props}" ]] || die "NDK source.properties not found under ${ANDROID_NDK_HOME}."
  local ndk_revision
  ndk_revision="$(grep -E "Pkg\.Revision" "${source_props}" | awk -F= '{print $2}' | tr -d '[:space:]')"
  if [[ "${ndk_revision}" != "${ANDROID_NDK_VERSION}" ]]; then
    die "NDK version mismatch: found ${ndk_revision}, expected ${ANDROID_NDK_VERSION}."
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
