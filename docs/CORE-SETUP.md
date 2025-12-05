# DigiByte Core checkout setup

_Status: Experimental / Early._ Digi-Mobile vendors the official DigiByte Core repository into `core/` for Android builds. The scripts do **not** modify consensus code.

## Who this is for
- Developers preparing the DigiByte Core tree before Android builds.
- Contributors verifying the repo references the expected upstream version.

## What you should have before starting
- git installed with network access to `https://github.com/DigiByte-Core/digibyte.git`.
- Basic familiarity with command-line tooling.

## Steps

1. **Run the setup helper** from the repo root:
   ```bash
   ./scripts/setup-core.sh
   ```
   - By default this uses `core/`, remote `https://github.com/DigiByte-Core/digibyte.git`, and ref `v8.26.1`.
   - Override as needed: `CORE_DIR=/tmp/core-test CORE_REMOTE_URL=https://github.com/DigiByte-Core/digibyte.git CORE_REF=develop ./scripts/setup-core.sh`.

2. **What the script does**
   - Creates `core/` if missing and initializes a git repo.
   - Detects non-git directories at `core/`, moves them aside, and recreates a clean repo.
   - Verifies the origin remote matches `CORE_REMOTE_URL` and corrects it if different.
   - Fetches tags and checks out the pinned `CORE_REF`, failing with a clear hint if the ref cannot be resolved.
   - It **does not** build DigiByte Core; that happens during the Android build (`./scripts/build-android.sh`), which cross-compiles `digibyted` and copies it into `android/app/src/main/assets/bin/` for Gradle to bundle.

## What could go wrong?
- Network/firewall issues can block fetching tags from GitHub.
- Local changes inside `core/` can prevent clean checkouts.
- Pointing `CORE_REMOTE_URL` at a mirror that lacks the desired tag will cause the ref check to fail.

## How to recover
- Re-run `./scripts/setup-core.sh` to fix the remote and re-fetch tags.
- Move or clean any local edits inside `core/` before rerunning the script.
- If fetches fail, retry with a different network or ensure the remote contains the requested ref.

## Related docs
- [`OVERVIEW`](OVERVIEW.md) for project context.
- [`CONFIGURATION`](CONFIGURATION.md) for picking Android-friendly runtime settings.
