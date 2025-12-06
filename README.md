# Digi-Mobile – Truncated DigiByte Core for Android

Digi-Mobile packages DigiByte Core as a pruned, Android-friendly full node build. The project keeps consensus and P2P behavior identical to upstream DigiByte Core while focusing on pruning-friendly defaults, cross-compiling, and Android packaging so devices can run a lightweight daemon and CLI.

**Status:** VERY EARLY, EXPERIMENTAL. Hobby/testing use only. Do not store serious funds. See [`docs/SECURITY-PRIVACY.md`](docs/SECURITY-PRIVACY.md) for disclaimers.

## Getting Started

Choose your path:

### 👤 Non-Technical Users – Install & Run
1. On your Android device, open this GitHub repo in a browser and go to **Releases**
2. Download the latest APK
3. Install it (enable "unknown sources" if prompted)
4. Open Digi-Mobile and tap **Set up and start node**

For step-by-step walkthrough, see [`docs/MOBILE-INSTALL.md`](docs/MOBILE-INSTALL.md).  
For a plain-English overview, see [`docs/GETTING-STARTED-NONTECH.md`](docs/GETTING-STARTED-NONTECH.md).

### 👨‍💻 Developers – Build from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/JohnnyLawDGB/Digi-Mobile.git
   cd Digi-Mobile
   ```
2. Follow the **one-command setup**:
   ```bash
   ./setup.sh
   ```
   This installs prerequisites, builds the daemon, and packages the APK.

For detailed steps, prerequisites, and troubleshooting, see [`docs/GETTING-STARTED-DEV.md`](docs/GETTING-STARTED-DEV.md).

### 🏗️ Advanced – Manual Build Steps
If you prefer to understand and run each step individually, see [`docs/GETTING-STARTED-DEV.md`](docs/GETTING-STARTED-DEV.md#manual-build-steps-advanced).

## Version & Disclaimers

**Digi-Mobile currently wraps DigiByte Core v8.26.1** (configurable via `CORE_REF`).

**⚠️ Disclaimers:**
- **EXPERIMENTAL:** This project is early-stage. Expect rough edges and breaking changes.
- **HOBBY USE ONLY:** Do not store serious amounts of DigiByte or rely on this for production custody.
- **NO LIABILITY:** Maintainers are not responsible for lost funds, data, or any other damages.
- **DEVICE RISK:** Android devices can be lost, stolen, or compromised. See [`docs/SECURITY-PRIVACY.md`](docs/SECURITY-PRIVACY.md).

## Design Principles

- **Minimal core changes:** Consensus and P2P logic remain untouched; pinned to known DigiByte Core release.
- **Pruning-first:** Optimized for storage-bounded nodes on mobile devices.
- **Transparent build:** Cross-compilation and asset staging are explicit and auditable.

## Contributing

Issues and roadmap items are tracked on [GitHub Issues](https://github.com/JohnnyLawDGB/Digi-Mobile/issues).

See [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md) for contribution guidelines, coding style, and good starting areas:
- JNI bridge improvements
- Android app UX and daemon integration
- CI/CD enhancements for reproducible builds
- Documentation improvements
