#!/usr/bin/env bash
set -euo pipefail

die() {
  echo "[Digi-Mobile] ERROR: $*" >&2
  exit 1
}

log() {
  echo "[Digi-Mobile] $*"
}

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null)" || die "Not inside a git repository."
cd "${ROOT_DIR}" || die "Failed to cd into repo root ${ROOT_DIR}"

DIGIMOBILE_CORE_REF_DEFAULT="v8.26.1"
REF="${DIGIMOBILE_CORE_REF:-${DIGIMOBILE_CORE_REF_DEFAULT}}"

log "Initializing/updating DigiByte Core submodule at ${ROOT_DIR}/core"
git submodule update --init --recursive core

[[ -d "core" ]] || die "core/ submodule missing after initialization"

cd core
log "Fetching DigiByte Core refs from origin"
git fetch --tags origin

if ! TARGET_COMMIT="$(git rev-parse "${REF}^{commit}" 2>/dev/null)"; then
  die "Unable to resolve DigiByte Core ref '${REF}'"
fi

CURRENT_COMMIT="$(git rev-parse HEAD)"
if [[ "${CURRENT_COMMIT}" == "${TARGET_COMMIT}" ]]; then
  log "core/ already at ${REF} [$(git rev-parse --short HEAD)]"
else
  log "Checking out DigiByte Core ref ${REF}"
  git checkout --detach "${TARGET_COMMIT}"
fi

SHORT_COMMIT="$(git rev-parse --short HEAD)"

cat <<SUMMARY
Digi-Mobile core setup complete.
Submodule: DigiByte-Core/digibyte
Checked out ref: ${REF} (commit ${SHORT_COMMIT})
Note: Digi-Mobile does NOT change DigiByte Core consensus rules; it just uses this version.
SUMMARY
