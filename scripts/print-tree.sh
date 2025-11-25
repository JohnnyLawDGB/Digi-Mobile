#!/usr/bin/env bash
# Render a shallow repository tree to orient contributors.
# Environment: requires Python 3 on PATH. Intended for local inspection only;
# does not touch DigiByte Core or Android builds.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

log() { echo "[Digi-Mobile] $*"; }

die() {
  echo "[Digi-Mobile] ERROR: $*" >&2
  exit 1
}

command -v python >/dev/null 2>&1 || die "python is required to print the tree"
cd "$ROOT_DIR"

log "Printing repository tree (depth 3) from ${ROOT_DIR}"
python - <<'PY'
import os
from pathlib import Path

root = Path('.')
max_depth = 3

print(root.resolve())

def display(path: Path, prefix: str = '', depth: int = 0):
    if depth >= max_depth:
        return
    entries = sorted(path.iterdir(), key=lambda p: (p.is_file(), p.name.lower()))
    for idx, entry in enumerate(entries):
        if entry.name.startswith('.git'):
            continue
        connector = '└── ' if idx == len(entries) - 1 else '├── '
        print(f"{prefix}{connector}{entry.name}")
        if entry.is_dir():
            extension = '    ' if idx == len(entries) - 1 else '│   '
            display(entry, prefix + extension, depth + 1)

display(root)
PY
