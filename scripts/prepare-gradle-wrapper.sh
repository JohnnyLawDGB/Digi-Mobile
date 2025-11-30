#!/usr/bin/env bash
set -euo pipefail

BASE64_FILE="$(dirname "$0")/../gradle/wrapper/gradle-wrapper.jar.base64"
TARGET_JAR="$(dirname "$0")/../gradle/wrapper/gradle-wrapper.jar"

if [[ -f "$TARGET_JAR" ]]; then
  exit 0
fi

if [[ ! -f "$BASE64_FILE" ]]; then
  echo "Gradle wrapper base64 file missing: $BASE64_FILE" >&2
  exit 1
fi

# Decode the wrapper JAR deterministically so it can be recreated without shipping binaries in the repo.
base64 --decode "$BASE64_FILE" > "$TARGET_JAR"
