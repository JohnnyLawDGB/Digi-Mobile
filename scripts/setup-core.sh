#!/usr/bin/env bash
# Initialize and validate the DigiByte Core submodule.
# Environment: requires git on PATH and internet access to fetch the pinned
# DigiByte Core reference in .versions/core-version.txt. Keeps consensus code
# unchanged; intended for local setup.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CORE_DIR="$ROOT_DIR/core"
VERSION_FILE="$ROOT_DIR/.versions/core-version.txt"
REPO_URL="https://github.com/digibyte/digibyte.git"

log() { echo "[Digi-Mobile] $*"; }

die() {
  echo "[Digi-Mobile] ERROR: $*" >&2
  exit 1
}

command -v git >/dev/null 2>&1 || die "git is required to manage the submodule"
[[ -f "$VERSION_FILE" ]] || die "Missing $VERSION_FILE. Cannot determine desired DigiByte Core version."
EXPECTED_REF=$(<"$VERSION_FILE")

cd "$ROOT_DIR"

if [[ ! -d "$CORE_DIR/.git" ]]; then
  log "Adding DigiByte Core submodule at $CORE_DIR"
  git submodule add "$REPO_URL" "$CORE_DIR"
fi

log "Initializing and updating the core submodule..."
git submodule update --init --recursive core

log "Checking out expected reference: $EXPECTED_REF"
if ! git -C "$CORE_DIR" fetch --tags --quiet; then
  log "Warning: failed to fetch tags for DigiByte Core. Continuing with existing refs."
fi

if ! git -C "$CORE_DIR" checkout "$EXPECTED_REF" --quiet; then
  die "Unable to checkout $EXPECTED_REF in core submodule."
fi

ACTIVE_REF=$(git -C "$CORE_DIR" describe --tags --exact-match 2>/dev/null || \
  git -C "$CORE_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || \
  git -C "$CORE_DIR" rev-parse --short HEAD)

STATUS="OK"
if [[ "$ACTIVE_REF" != "$EXPECTED_REF" ]]; then
  STATUS="MISMATCH"
fi

cat <<SUMMARY
[Digi-Mobile] DigiByte Core submodule status:
  Location : $CORE_DIR
  Expected : $EXPECTED_REF
  Active   : $ACTIVE_REF
  Result   : $STATUS
SUMMARY

if [[ "$STATUS" != "OK" ]]; then
  die "Core submodule is not on the expected reference."
fi
