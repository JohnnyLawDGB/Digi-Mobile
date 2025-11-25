# Digi-Mobile Configuration Profiles

Digi-Mobile ships curated DigiByte Core configuration examples under `config/` to simplify running on Android hardware. These profiles can be packaged with the app (e.g., as assets) and copied into an app-private `datadir` during first run. Values are tuned for limited storage/RAM and intermittent connectivity typical of phones.

## Provided configs

### `config/digimobile-pruned.conf`
* Target audience: everyday Android phones with roughly 10–20 GB free.
* Pruning enabled (3 GB target) to keep disk usage small while maintaining full consensus validation. Smaller values save space but increase re-downloads during rescans; larger values reduce network churn at the cost of storage.
* Wallet disabled by default to save memory and background I/O; a commented section shows how to re-enable it when needed.
* Minimal logging and services enabled—no RPC binding by default and conservative networking limits suitable for mobile data.

### `config/digimobile-dev.conf`
* Target audience: emulator or rooted device testing where extra visibility is helpful.
* Pruning enabled (3.5 GB target) with slightly looser resource limits for debugging.
* RPC enabled by default but bound to `127.0.0.1` with warnings about exposing it over Wi‑Fi/cellular.
* Verbose console logging to aid troubleshooting; disable or narrow debug topics once issues are resolved.

## Choosing a profile

* Use **`digimobile-pruned.conf`** for production-like mobile deployments that prioritize storage, battery, and background I/O.
* Use **`digimobile-dev.conf`** when you need RPC access, console logs, or additional peers during development.

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

* During app packaging, include the desired config under `assets/` or a similar bundle location.
* On first run, copy the config into an app-private directory such as `/data/data/<package>/files/.digibyte` or `/storage/emulated/0/Android/data/<package>/files/.digibyte` and pass it via `-conf`.
* RPC should remain bound to localhost on Android. If you must connect externally, use emulator loopback or secure tunnels/port forwarding rather than exposing RPC over Wi‑Fi or cellular networks.
