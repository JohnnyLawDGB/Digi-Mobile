# Core Versioning and DigiByte Relationship

Digi-Mobile does not re-implement DigiByte; it vendors the official DigiByte Core repository in `./core`.
By default, `scripts/setup-core.sh` pins DigiByte Core to tag `v8.26.1` (the v8.26 series).
Maintainers can override the ref by setting `CORE_REF` before running the script, e.g., to test `v8.26.0` or a future tag, and can point to a different mirror with `CORE_REMOTE_URL`.

Consensus and P2P behavior come directly from DigiByte Core. Digi-Mobile layers pruning-friendly defaults, Android packaging, and daemon orchestration without altering consensus rules.

## How to use this

- Run `./scripts/setup-core.sh` to pull and pin DigiByte Core.
- Use `git -C core status` or `git -C core rev-parse HEAD` to see which commit is checked out.
- Remember this is hobby/experimental software; avoid storing irreplaceable data and use only spare hardware.
