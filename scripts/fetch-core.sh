#!/usr/bin/env bash
# Fetch and pin the DigiByte Core submodule to the expected version.
# Environment: requires git available on PATH and access to the pinned version
# noted in .versions/core-version.txt. Intended for local cloning/updates;
# consensus code remains untouched.
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
[[ -f "$VERSION_FILE" ]] || die "Missing $VERSION_FILE; expected pinned DigiByte Core version"
CORE_REF=$(<"$VERSION_FILE")

if [[ ! -d "$CORE_DIR/.git" ]]; then
  log "Initializing DigiByte Core submodule..."
  git submodule add "$REPO_URL" "$CORE_DIR"
fi

log "Fetching DigiByte Core reference ${CORE_REF}..."
cd "$CORE_DIR"
git fetch --tags

if ! git checkout "$CORE_REF"; then
  die "Failed to checkout ${CORE_REF}. Update .versions/core-version.txt if needed."
fi

PATCH_DIR="$ROOT_DIR/android/patches"
if compgen -G "$PATCH_DIR/*.patch" > /dev/null; then
  log "Applying Android-specific patches..."
  for patch in "$PATCH_DIR"/*.patch; do
    log "Applying $patch"
    git apply "$patch"
  done
fi

log "DigiByte Core is ready at $CORE_DIR"
