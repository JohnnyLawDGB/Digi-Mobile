#!/usr/bin/env bash
set -euo pipefail

# Initialize or update the DigiByte Core submodule to v8.26.
# Usage: ./scripts/fetch-core.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CORE_DIR="$ROOT_DIR/core"
VERSION_FILE="$ROOT_DIR/.versions/core-version.txt"

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "Missing $VERSION_FILE; expected to find pinned DigiByte Core version." >&2
  exit 1
fi

CORE_REF=$(cat "$VERSION_FILE")

if [[ ! -d "$CORE_DIR/.git" ]]; then
  echo "Initializing DigiByte Core submodule..."
  git submodule add https://github.com/digibyte/digibyte.git "$CORE_DIR"
fi

echo "Fetching DigiByte Core reference ${CORE_REF}..."
cd "$CORE_DIR"
git fetch --tags
# Checkout the pinned tag/commit
if ! git checkout "$CORE_REF"; then
  echo "Failed to checkout ${CORE_REF}." >&2
  exit 1
fi

# Apply any Android-specific patches if present
PATCH_DIR="$ROOT_DIR/android/patches"
if compgen -G "$PATCH_DIR/*.patch" > /dev/null; then
  echo "Applying Android-specific patches..."
  for patch in "$PATCH_DIR"/*.patch; do
    echo "Applying $patch"
    git apply "$patch"
  done
fi

echo "DigiByte Core is ready at $CORE_DIR"
