# Digi-Mobile: Plain-English Getting Started Guide

> **THIS IS HIGHLY EXPERIMENTAL SOFTWARE. DO NOT INSTALL ON ANYTHING OTHER THAN SPARE HARDWARE YOU WOULDN’T MIND BRICKING.**

## What is Digi-Mobile?
Digi-Mobile turns a spare Android phone or tablet into a **pruned DigiByte relay node**. It runs a trimmed-down copy of the DigiByte blockchain (recent history only) and helps check transactions and blocks for the network. Wallet functionality is intentionally **not present** in this pre-release.

## What Digi-Mobile is GOOD for
- Using a spare phone/tablet at home to help the DigiByte network as a small relay node.
- Learning how full nodes work in a hands-on way without buying special hardware.
- Running a hobby node you do not depend on for anything mission-critical.
- Acting like a tiny personal server for DigiByte—useful for tinkering, not an everyday wallet.

## What Digi-Mobile is NOT for
- **Do not store your life savings or large amounts of DigiByte here.**
- Do not use this as your only backup of keys or recovery phrases.
- Do not use this for institutional custody, business treasury, or regulated financial services.
- Do not expect it to run 24/7 without hiccups—phones die, SD cards fail, background apps get killed.
- Remember: this is experimental software and things can break.

## Important Disclaimers
- **THIS IS HIGHLY EXPERIMENTAL SOFTWARE. DO NOT INSTALL ON ANYTHING OTHER THAN SPARE HARDWARE YOU WOULDN’T MIND BRICKING.**
- Relay-node-only build; there is no wallet UI.
- The maintainers and contributors are not liable for lost funds, data, or anything else.
- Digi-Mobile is a hobby/community tool to help the network, not a commercial product.

## Security & Privacy Basics
- Use only spare hardware; losing or bricking the device should not harm you.
- Running a node shares network traffic with peers, so avoid exposing RPC to the internet unless you know how to secure it.
- Read the full details in [`docs/SECURITY-PRIVACY.md`](./SECURITY-PRIVACY.md).

## How a non-technical user might actually use this
1. You have a spare Android phone with at least **4 GB RAM** and ~4–5 GB free space.
2. Download the APK from the **Releases** page on this GitHub repo and install it (enable unknown sources if prompted).
3. Open the app and tap **Set up and start node**. The app will prepare the data directory and start the pruned relay node.
4. Keep the device plugged in and on Wi‑Fi; the initial sync is long-running even in pruned mode.
5. Advanced users can still reach the daemon over RPC/CLI using adb or another client.

## Concepts in simple terms
- **Full node vs light wallet:** A full node checks the rules itself and shares data with others; a light wallet trusts someone else to do most of the checking.
- **Pruned node:** Keeps recent blockchain data and throws away older blocks locally to save space, while still checking new activity.
- **Why run a node:** More independent nodes mean more resilience and decentralization for the DigiByte network.

## Where to go next
- Start with [`README.md`](../README.md) for the project overview.
- Technical setup lives in [`docs/RUNNING-ON-DEVICE.md`](./RUNNING-ON-DEVICE.md) for device steps.
- Advanced settings are in [`docs/CONFIGURATION.md`](./CONFIGURATION.md).
- If you are not technical, ask a trusted technical helper to walk through those docs with you.
