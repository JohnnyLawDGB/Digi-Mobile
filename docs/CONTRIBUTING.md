# Contributing to Digi-Mobile

_Status: Experimental / Early._ Digi-Mobile is a community/hobby project that packages a truncated/pruned DigiByte node for Android. Contributions that improve clarity and robustness are welcome.

## Expectations
- Keep changes minimal and well-documented; avoid altering DigiByte consensus-critical code unless absolutely necessary.
- When changing behavior, update inline comments and relevant docs alongside code.
- Prefer incremental pull requests over large rewrites.

## Style guidance
- **Scripts:** Bash with `#!/usr/bin/env bash` and `set -euo pipefail`; include short headers describing environment expectations.
- **Docs:** Markdown with short headings, plain language, and cross-links to related files.
- **C++/CMake:** Favor clear comments and obvious flags over clever tricks; keep Android options configurable where reasonable.

## Before opening a PR
- Explain the motivation and scope; note any assumptions about devices/NDK versions.
- Mention whether tests or device runs were performed.
- Highlight any safety considerations (e.g., RPC exposure, storage usage).
