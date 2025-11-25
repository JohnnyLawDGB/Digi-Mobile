# DigiByte Core upstream

This directory is intended to host the DigiByte Core v8.26 source as a git submodule.
Initialize it by running `scripts/fetch-core.sh`, which pins the submodule to the desired tag/commit.
No modifications to consensus or networking behavior should be made here; Android-specific build glue lives under `/android` and `/scripts`.
