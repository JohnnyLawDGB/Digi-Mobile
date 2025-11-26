#!/usr/bin/env bash
# Ensure the core/ directory contains a clean DigiByte Core checkout. This tree is vendored for
# Android builds only; Digi-Mobile does not modify DigiByte Core consensus code here. Override
# CORE_REF to pin a different ref (e.g., CORE_REF=develop ./scripts/setup-core.sh).

set -u
set -o pipefail

die() {
  echo "[Digi-Mobile] ERROR: $*" >&2
  exit 1
}

log() {
  echo "[Digi-Mobile] $*"
}

CORE_DIR="${CORE_DIR:-core}"
CORE_REMOTE_URL="${CORE_REMOTE_URL:-https://github.com/DigiByte-Core/digibyte.git}"
CORE_REF="${CORE_REF:-v8.26.1}"

get_core_origin() {
  git -C "$CORE_DIR" remote get-url origin 2>/dev/null || echo ""
}

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null)" || die "Not inside a git repository."
cd "${ROOT_DIR}" || die "Failed to cd into repo root ${ROOT_DIR}"

log "Using DigiByte Core directory: ${CORE_DIR}"
log "Using DigiByte Core remote: ${CORE_REMOTE_URL}"
log "Target DigiByte Core ref: ${CORE_REF}"

if [[ ! -d "${CORE_DIR}" ]]; then
  log "${CORE_DIR}/ is missing. Creating a fresh DigiByte Core repo."
  mkdir -p "${CORE_DIR}" || die "Failed to create ${CORE_DIR}/"
  git -C "${CORE_DIR}" init || die "Failed to initialize git repo in ${CORE_DIR}/"
  git -C "${CORE_DIR}" remote add origin "${CORE_REMOTE_URL}" || die "Failed to add origin remote"
elif [[ -d "${CORE_DIR}/.git" ]]; then
  ORIGIN_URL="$(get_core_origin)"
  if [[ "${ORIGIN_URL}" != "${CORE_REMOTE_URL}" ]]; then
    echo "[Digi-Mobile] WARNING: ${CORE_DIR}/ origin remote '${ORIGIN_URL}' differs from expected '${CORE_REMOTE_URL}'. Correcting it." >&2
    if git -C "${CORE_DIR}" remote set-url origin "${CORE_REMOTE_URL}" 2>/dev/null; then
      log "Updated origin remote to ${CORE_REMOTE_URL}"
    else
      git -C "${CORE_DIR}" remote add origin "${CORE_REMOTE_URL}" || die "Failed to add origin remote"
    fi
  fi
else
  BROKEN_DIR="${CORE_DIR}.broken.$(date +%s)"
  echo "[Digi-Mobile] WARNING: ${CORE_DIR}/ exists but is not a git repo. Moving it to ${BROKEN_DIR}" >&2
  mv "${CORE_DIR}" "${BROKEN_DIR}" || die "Failed to move broken ${CORE_DIR}/ aside"
  mkdir -p "${CORE_DIR}" || die "Failed to recreate ${CORE_DIR}/"
  git -C "${CORE_DIR}" init || die "Failed to initialize git repo in ${CORE_DIR}/"
  git -C "${CORE_DIR}" remote add origin "${CORE_REMOTE_URL}" || die "Failed to add origin remote"
fi

echo "[Digi-Mobile] Fetching DigiByte Core refs from ${CORE_REMOTE_URL}"
if ! git -C "${CORE_DIR}" fetch --tags --prune origin; then
  echo "[Digi-Mobile] ERROR: Failed to fetch from DigiByte Core remote." >&2
  exit 1
fi

if git -C "${CORE_DIR}" show-ref --verify --quiet "refs/tags/${CORE_REF}"; then
  git -C "${CORE_DIR}" checkout -f "tags/${CORE_REF}" || die "Failed to checkout DigiByte Core tag ${CORE_REF}"
elif git -C "${CORE_DIR}" rev-parse --verify --quiet "${CORE_REF}"; then
  git -C "${CORE_DIR}" checkout -f "${CORE_REF}" || die "Failed to checkout DigiByte Core ref ${CORE_REF}"
else
  echo "[Digi-Mobile] ERROR: Unable to resolve DigiByte Core ref '${CORE_REF}' in ${CORE_REMOTE_URL}" >&2
  echo "[Digi-Mobile] Hint: check https://github.com/DigiByte-Core/digibyte/tags to confirm tag names." >&2
  exit 1
fi

SHORT_COMMIT="$(git -C "${CORE_DIR}" rev-parse --short HEAD)" || die "Failed to resolve checked-out commit"

cat <<SUMMARY
Digi-Mobile core setup complete.
Repository: DigiByte-Core/digibyte
Checked out ref: ${CORE_REF} (commit ${SHORT_COMMIT})
Note: Digi-Mobile does NOT change DigiByte Core consensus rules; it just uses this version.
SUMMARY
