# DigiByte Core submodule setup

_Status: Experimental / Early._ Digi-Mobile keeps a truncated/pruned DigiByte node for Android. This guide pins the DigiByte Core submodule without changing consensus logic.

## Who this is for
- Developers preparing the DigiByte Core tree before Android builds.
- Contributors verifying the repo references the expected upstream version.

## What you should have before starting
- git installed with network access to `github.com/digibyte/digibyte`.
- Basic familiarity with submodules and command-line tooling.

## Steps

1. **Add the submodule** (if missing):
   ```bash
   git submodule add https://github.com/digibyte/digibyte.git ./core
   ```

2. **Check out the expected release** using `.versions/core-version.txt`:
   ```bash
   cd core
   git fetch --tags
   CORE_REF=$(cat ../.versions/core-version.txt)
   git checkout "$CORE_REF"
   ```

3. **Refresh an existing submodule** after pulling repo updates:
   ```bash
   git submodule update --init --recursive core
   ```

## What could go wrong?
- Missing `.versions/core-version.txt` means the pinned reference is unknown.
- Network/firewall issues can block fetching tags.
- Local changes inside `core/` can prevent clean checkouts.

## How to recover
- Regenerate the submodule by removing `core/` then re-running the steps above.
- Use `git -C core status` to spot local modifications before trying another checkout.
- If fetches fail, retry with a different network or mirror.

## Related docs
- [`OVERVIEW`](OVERVIEW.md) for project context.
- [`CONFIGURATION`](CONFIGURATION.md) for picking Android-friendly runtime settings.
