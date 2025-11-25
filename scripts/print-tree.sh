#!/usr/bin/env bash
set -euo pipefail

# Print a lightweight tree of the repository to orient contributors.
# Shows directories and top-level files up to a depth of 3, skipping .git internals.

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# Use Python to render a predictable depth-limited tree without requiring extra packages.
python - <<'PY'
import os
from pathlib import Path

root = Path('.')
max_depth = 3

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

print(root.resolve())
display(root)
PY
