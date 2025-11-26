# Core Versioning and DigiByte Relationship

Digi-Mobile does not re-implement DigiByte; it wraps the official DigiByte Core repository as a submodule in `./core`.
By default, `scripts/setup-core.sh` pins DigiByte Core to tag `v8.26.1` (the v8.26 series).
Maintainers can override the ref by setting `DIGIMOBILE_CORE_REF` before running the script, e.g., to test `v8.26.0` or a future tag.

Consensus and P2P behavior come from DigiByte Core itself. Digi-Mobile focuses on pruning-friendly defaults and packaging for Android without altering consensus rules.

## How to use this

- Run `./scripts/setup-core.sh` to pull and pin DigiByte Core.
- Use `git submodule status core` to see which commit is checked out.
- Remember this is hobby/experimental software; avoid storing large balances here.
