# Digi-Mobile Configuration Profiles

_Status: Experimental / Early._ Digi-Mobile ships curated examples for running a truncated/pruned DigiByte node on Android. These templates may need tweaks for your device.

## Who this is for
- Android developers bundling DigiByte Core configs into apps.
- Power users choosing pruned settings for small devices.

## What you should have before starting
- A built DigiByte daemon (`digibyted`) from the Android toolchain.
- Basic familiarity with DigiByte Core configuration files.
- adb or packaging flow ready to place configs on the device.

## Provided configs

### `config/digimobile-pruned.conf`
- Target audience: everyday Android phones with roughly 10–20 GB free.
- Pruning enabled (3 GB target) to keep disk usage small while maintaining full consensus validation. Smaller values save space but increase re-downloads during rescans; larger values reduce network churn at the cost of storage.
- Wallet disabled by default to save memory and background I/O; a commented section shows how to re-enable it when needed.
- Minimal logging and services enabled—no RPC binding by default and conservative networking limits suitable for mobile data.

### `config/digimobile-dev.conf`
- Target audience: emulator or rooted device testing where extra visibility is helpful.
- Pruning enabled (3.5 GB target) with slightly looser resource limits for debugging.
- RPC enabled by default but bound to `127.0.0.1` with warnings about exposing it over Wi‑Fi/cellular.
- Verbose console logging to aid troubleshooting; disable or narrow debug topics once issues are resolved.

## Choosing a profile
- Use **`digimobile-pruned.conf`** for production-like mobile deployments that prioritize storage, battery, and background I/O.
- Use **`digimobile-dev.conf`** when you need RPC access, console logs, or additional peers during development.

## Overriding pruning or wallet options
Command-line flags override values from the config file. Examples:

```bash
# Raise prune target to 4 GB for more cached history
./digibyted -conf=config/digimobile-pruned.conf -prune=4000

# Enable wallet while using the pruned profile
./digibyted -conf=config/digimobile-pruned.conf -disablewallet=0
```

Note: Pruning preserves consensus security but limits locally stored history. Wallet rescans may need to download missing blocks when using small prune targets.

## Android integration notes
- During app packaging, include the desired config under `assets/` or a similar bundle location.
- On first run, copy the config into an app-private directory such as `/data/data/<package>/files/.digibyte` or `/storage/emulated/0/Android/data/<package>/files/.digibyte` and pass it via `-conf`.
- RPC should remain bound to localhost on Android. If you must connect externally, use emulator loopback or secure tunnels/port forwarding rather than exposing RPC over Wi‑Fi or cellular networks.

## What could go wrong?
- Using too small a prune target can slow wallet rescans or require re-downloads.
- Accidentally enabling RPC on non-local interfaces can expose control of the node.
- Device storage may fill if logs are left verbose on small phones.

## How to recover
- Raise `prune` and free storage if rescans keep failing due to re-fetching.
- Revert to localhost-only RPC settings and restart the daemon.
- Rotate or pull logs with [`RUNNING-ON-DEVICE`](RUNNING-ON-DEVICE.md) helpers to clean up space.
