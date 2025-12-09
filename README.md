# Digi-Mobile – Truncated DigiByte Core for Android

> **THIS IS HIGHLY EXPERIMENTAL SOFTWARE. DO NOT INSTALL ON ANYTHING OTHER THAN SPARE HARDWARE YOU WOULDN’T MIND BRICKING.**

Digi-Mobile packages DigiByte Core as a pruned, Android-friendly node build. Core consensus and P2P logic remain identical to DigiByte Core v8.26.x; this project focuses on pruning defaults, Android packaging, and daemon orchestration.

## What this pre-release does

- **Relay node only (no wallet UI):** The APK runs the DigiByte daemon as a foreground service and exposes console/RPC commands; wallet features are not present in this build.
- **Storage target:** ~**3.2 GB** for the truncated chain state.
- **Memory expectations:** Tested on devices with **4 GB RAM**.
- **Peer limit:** Max peers = **8** (intentional for mobile stability).
- **Sync behavior:** Initial blockchain sync is long-running even in pruned mode; an archival bootstrap file will not trivially skip this.

## Device requirements

- 64-bit Android device (arm64-v8a) with at least **4 GB RAM**
- ~4–5 GB free space to accommodate the ~3.2 GB pruned data target and overhead
- Android 9+ recommended

## Getting Started

### 👤 Non-Technical Users – Install & Run on Android
1. On your Android device, open this GitHub repo and tap **Releases**.
2. Download the latest APK and install it (allow "unknown sources" if prompted).
3. Open Digi-Mobile and tap **Set up and start node** to create data folders and start the relay node.

Details and screenshots: [`docs/MOBILE-INSTALL.md`](docs/MOBILE-INSTALL.md) and [`docs/GETTING-STARTED-NONTECH.md`](docs/GETTING-STARTED-NONTECH.md).

### 👨‍💻 Developers – Build from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/JohnnyLawDGB/Digi-Mobile.git
   cd Digi-Mobile
   ```
2. Run the **one-command setup**:
   ```bash
   ./setup.sh
   ```
   Installs prerequisites, builds the daemon, and packages the APK. Use `./status.sh` to check the node and `./stop.sh` to stop it.

Advanced/manual steps live in [`docs/GETTING-STARTED-DEV.md`](docs/GETTING-STARTED-DEV.md#manual-build-steps-advanced).

## Architecture

- DigiByte Core v8.26.x is vendored in `./core` (default tag `v8.26.1`); override with `CORE_REF` if needed.
- Consensus and P2P behavior are unchanged from DigiByte Core; this project only layers pruning-friendly defaults, Android packaging, and daemon orchestration.

See [`docs/CORE-VERSIONING.md`](docs/CORE-VERSIONING.md) for version pin details.

## Roadmap (next steps)

- Interactive node configuration based on device capabilities
- Improved initial synchronization behavior and UX
- Minimal core wallet functionality (future, not present today)
- Hardening and security assessment

## Contributing

Issues and roadmap items are tracked on [GitHub Issues](https://github.com/JohnnyLawDGB/Digi-Mobile/issues).

See [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md) for contribution guidelines and good starting areas.
