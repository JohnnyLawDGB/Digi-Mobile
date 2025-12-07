# Documentation Index

This folder contains comprehensive guides for users, developers, and contributors.

## Quick Navigation

### 👤 End Users (Non-Technical)
- **[GETTING-STARTED-NONTECH.md](GETTING-STARTED-NONTECH.md)** – Plain-English overview and use cases
- **[MOBILE-INSTALL.md](MOBILE-INSTALL.md)** – Download and install the APK on Android
- **[SECURITY-PRIVACY.md](SECURITY-PRIVACY.md)** – Security and privacy considerations (important!)

### 👨‍💻 Developers (Building from Source)
- **[GETTING-STARTED-DEV.md](GETTING-STARTED-DEV.md)** – Prerequisites, one-command setup, troubleshooting
- **[ADVANCED-ANDROID-BUILD.md](ADVANCED-ANDROID-BUILD.md)** – Detailed build process, customization, internals
- **[CORE-SETUP.md](CORE-SETUP.md)** – DigiByte Core checkout initialization and management
- **[CONFIGURATION.md](CONFIGURATION.md)** – Runtime settings (pruning, ports, memory, etc.)

### 🏗️ Architecture & Deep Dives
- **[OVERVIEW.md](OVERVIEW.md)** – High-level architecture, design principles, key directories
- **[JNI-BRIDGE.md](JNI-BRIDGE.md)** – JNI integration between Kotlin app and C++ daemon
- **[PRUNING.md](PRUNING.md)** – Pruned node behavior and storage optimization
- **[RPC-CONSOLE.md](RPC-CONSOLE.md)** – Using the RPC console and running commands

### 📱 Device Management
- **[RUNNING-ON-DEVICE.md](RUNNING-ON-DEVICE.md)** – adb-based deployment, monitoring, and maintenance
- **[ANDROID-INSTALL.md](ANDROID-INSTALL.md)** – Pushing binaries to devices and Termux environments

### 🛠️ Project Maintenance
- **[CONTRIBUTING.md](CONTRIBUTING.md)** – Contribution guidelines, coding standards, PR process
- **[CORE-VERSIONING.md](CORE-VERSIONING.md)** – Managing DigiByte Core version pins and upgrades
- **[gradle-wrapper-conflict-resolution.md](gradle-wrapper-conflict-resolution.md)** – Fixing Gradle wrapper merge conflicts

### 🔧 Environment & Setup
- **[ENVIRONMENT-UBUNTU-22.04.md](ENVIRONMENT-UBUNTU-22.04.md)** – Full environment setup for Ubuntu 22.04

---

## Recommended Reading Order

### For Non-Technical Users
1. Start with the main [README](../README.md)
2. Read [GETTING-STARTED-NONTECH.md](GETTING-STARTED-NONTECH.md)
3. Follow [MOBILE-INSTALL.md](MOBILE-INSTALL.md)
4. Review [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md) **before using**

### For Developers (First Time)
1. Start with the main [README](../README.md)
2. Read [OVERVIEW.md](OVERVIEW.md) for architecture context
3. Follow [GETTING-STARTED-DEV.md](GETTING-STARTED-DEV.md) – run `./setup.sh`
4. Refer to [CONFIGURATION.md](CONFIGURATION.md) to tune settings
5. Check [RUNNING-ON-DEVICE.md](RUNNING-ON-DEVICE.md) to deploy

### For Developers (Customizing Build)
1. Read [ADVANCED-ANDROID-BUILD.md](ADVANCED-ANDROID-BUILD.md)
2. Review [CORE-SETUP.md](CORE-SETUP.md) for version management
3. Check [CORE-VERSIONING.md](CORE-VERSIONING.md) for pinning strategies

### For Contributors
1. Read [CONTRIBUTING.md](CONTRIBUTING.md)
2. Review the architecture in [OVERVIEW.md](OVERVIEW.md)
3. Understand the build process via [ADVANCED-ANDROID-BUILD.md](ADVANCED-ANDROID-BUILD.md)
4. Check [JNI-BRIDGE.md](JNI-BRIDGE.md) for app integration details

---

## Key Concepts

**Digi-Mobile** is an Android app that:
- Bundles DigiByte Core (v8.26.1 by default) for arm64 Android devices
- Runs the daemon as a foreground service
- Exposes core functionality via JNI to the Kotlin app
- Supports pruned (storage-efficient) node operation

**Build flow:**
1. DigiByte Core source checked out via `./scripts/setup-core.sh`
2. Cross-compiled for Android via `./scripts/build-android.sh`
3. Binaries staged into Android app assets
4. APK assembled and signed by Gradle
5. Installed on device and run as a service

**Recommended setup:**
- Run `./setup.sh` in the repo root (one-command wizard)
- Or follow manual steps in [GETTING-STARTED-DEV.md](GETTING-STARTED-DEV.md#manual-build-steps-advanced)

---

## Quick Links

- **GitHub Repository:** https://github.com/JohnnyLawDGB/Digi-Mobile
- **Issue Tracker:** https://github.com/JohnnyLawDGB/Digi-Mobile/issues
- **Releases:** https://github.com/JohnnyLawDGB/Digi-Mobile/releases
- **DigiByte Core:** https://github.com/DigiByte-Core/digibyte

---

## Document Status

| Document | Status | Last Updated |
|----------|--------|--------------|
| GETTING-STARTED-DEV.md | ✅ Active | 2025-12-06 |
| GETTING-STARTED-NONTECH.md | ✅ Active | Current |
| ADVANCED-ANDROID-BUILD.md | ✅ Active | 2025-12-06 |
| OVERVIEW.md | ✅ Active | Current |
| CORE-SETUP.md | ✅ Active | Current |
| SECURITY-PRIVACY.md | ✅ Active | Current |
| CONFIGURATION.md | ✅ Active | Current |
| JNI-BRIDGE.md | ✅ Active | Current |
| RUNNING-ON-DEVICE.md | ✅ Active | Current |
| CONTRIBUTING.md | ✅ Active | Current |

---

## Need Help?

1. Check the relevant guide for your use case (see "Recommended Reading Order" above)
2. Search [GitHub Issues](https://github.com/JohnnyLawDGB/Digi-Mobile/issues) for similar problems
3. Review [GETTING-STARTED-DEV.md#troubleshooting](GETTING-STARTED-DEV.md#troubleshooting) for common errors
4. Open a new issue if your problem isn't covered
