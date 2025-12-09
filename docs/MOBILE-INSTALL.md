
# Install Digi-Mobile on Android (APK download)

These steps are for non-technical users who want to try Digi-Mobile on an Android device **without building anything**.

> **THIS IS HIGHLY EXPERIMENTAL SOFTWARE. DO NOT INSTALL ON ANYTHING OTHER THAN SPARE HARDWARE YOU WOULDN’T MIND BRICKING.**
> Relay node only in this build; there is no wallet UI.

## Install from Android using your browser

1. On your Android phone or tablet, open this GitHub repository in Chrome, Firefox, or another browser.
2. Tap the **Releases** section.
3. Download the latest Digi-Mobile APK.
4. If your device asks, allow installs from "unknown sources" so the APK can be installed.
5. Open the Digi-Mobile app after installation.
6. Tap **Set up and start node** inside the app to create the config/data folders and start the pruned DigiByte relay node.

### What to expect

- **Storage:** Targets ~3.2 GB for the truncated chain; keep 4–5 GB free for safety.
- **Memory:** Tested on devices with **4 GB RAM**.
- **Peers:** Max peers = **8** (intentional for mobile stability).
- **Sync time:** Initial blockchain sync is long-running even in pruned mode; a bootstrap archive will not trivially skip it.

## FAQ

- **Is this in the Play Store?**
  - Not yet. This build is experimental and meant for sideloading while the project stabilizes.
- **Why do I see warnings about unknown apps?**
  - Android shows this when you sideload (install from outside the Play Store). Use a device you are comfortable experimenting with.
- **What if it does not start?**
  - Close the app and re-open it, then tap **Set up and start node** again. The app will re-check the bundled files and try to start the node.
- **Does this work on every phone?**
  - The APK ships only an **arm64-v8a** native library and daemon. 64-bit Android devices (most modern flagships) are supported; 32-bit-only devices will crash on launch with a missing-library error. If the app closes immediately on tap, verify your device reports arm64/aarch64 under its CPU/ABI info before retrying.
- **Why is there no console/terminal in the app?**
  - Digi-Mobile intentionally ships a simple single-screen activity that boots the bundled DigiByte daemon as a foreground service. It shows status text and a toast after you tap **Set up and start node** but does not expose an interactive CLI. To run RPC/CLI commands you must use external tools (e.g., `adb logcat`, `adb shell`, or a separate client) against the running service.
- **Does this build include a wallet?**
  - No. This pre-release is relay-node-only with a deliberate max peer count of 8. RPC commands remain available for advanced users.

## Technical details

- The app requests the standard `INTERNET` permission so the DigiByte node can reach peers. This is expected for any full node and is required for the node to function.

## More resources

- Read the plain-language overview: [`docs/GETTING-STARTED-NONTECH.md`](GETTING-STARTED-NONTECH.md)
- Security and privacy notes: [`docs/SECURITY-PRIVACY.md`](SECURITY-PRIVACY.md)
