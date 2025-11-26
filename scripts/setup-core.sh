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
FALLBACK_REF="v8.26.0"

log "Initializing/updating DigiByte Core submodule at ${ROOT_DIR}/core"
git submodule update --init --recursive core

[[ -d "core" ]] || die "core/ submodule missing after initialization"

cd core
log "Fetching DigiByte Core refs from origin"
git fetch --tags origin

# Resolve the requested ref. If it is missing, try the fallback ref (v8.26.0).
# This allows setup to succeed even if the default ref is not present in the
# submodule checkout while still surfacing the original request to the user.
if ! TARGET_COMMIT="$(git rev-parse "${REF}^{commit}" 2>/dev/null)"; then
  if TARGET_COMMIT="$(git rev-parse "${FALLBACK_REF}^{commit}" 2>/dev/null)"; then
    echo "[Digi-Mobile] WARNING: ${REF} not found, falling back to ${FALLBACK_REF}" >&2
    REF="${FALLBACK_REF}"
  else
    AVAILABLE_TAGS=$(git tag -l "v8.*" | sort -V)
    die "Unable to resolve DigiByte Core ref '${REF}'. Tried fallback '${FALLBACK_REF}', but it was not found. Available v8.x tags:\n${AVAILABLE_TAGS:-<none>}"
  fi
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
