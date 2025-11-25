#!/usr/bin/env bash
set -euo pipefail

# Initialize and validate the DigiByte Core submodule.
# - Adds the submodule if it does not exist.
# - Updates and checks out the expected tag/branch from .versions/core-version.txt.
# - Prints a concise status summary when finished.

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CORE_DIR="$ROOT_DIR/core"
VERSION_FILE="$ROOT_DIR/.versions/core-version.txt"
REPO_URL="https://github.com/digibyte/digibyte.git"

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "Missing $VERSION_FILE. Cannot determine desired DigiByte Core version." >&2
  exit 1
fi

EXPECTED_REF=$(<"$VERSION_FILE")

cd "$ROOT_DIR"

# Add the submodule if it has not been initialized yet.
if [[ ! -d "$CORE_DIR/.git" ]]; then
  echo "Adding DigiByte Core submodule at $CORE_DIR"
  git submodule add "$REPO_URL" "$CORE_DIR"
fi

# Ensure submodule contents are present.
echo "Initializing and updating the core submodule..."
git submodule update --init --recursive core

# Ensure we are on the expected tag/branch.
echo "Checking out expected reference: $EXPECTED_REF"
if ! git -C "$CORE_DIR" fetch --tags --quiet; then
  echo "Warning: failed to fetch tags for DigiByte Core. Continuing with existing refs." >&2
fi

if ! git -C "$CORE_DIR" checkout "$EXPECTED_REF" --quiet; then
  echo "Unable to checkout $EXPECTED_REF in core submodule." >&2
  exit 1
fi

# Determine the active ref for reporting.
ACTIVE_REF=$(git -C "$CORE_DIR" describe --tags --exact-match 2>/dev/null || \
  git -C "$CORE_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || \
  git -C "$CORE_DIR" rev-parse --short HEAD)

STATUS="OK"
if [[ "$ACTIVE_REF" != "$EXPECTED_REF" ]]; then
  STATUS="MISMATCH"
fi

cat <<SUMMARY
DigiByte Core submodule status:
  Location : $CORE_DIR
  Expected : $EXPECTED_REF
  Active   : $ACTIVE_REF
  Result   : $STATUS
SUMMARY

if [[ "$STATUS" != "OK" ]]; then
  exit 1
fi
