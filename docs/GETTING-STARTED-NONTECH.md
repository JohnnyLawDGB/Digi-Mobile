# Digi-Mobile: Plain-English Getting Started Guide

## What is Digi-Mobile?
Digi-Mobile turns a spare Android phone or tablet into a **pruned DigiByte node**. That means it runs a trimmed-down copy of the DigiByte blockchain (recent history only) and helps check transactions and blocks for the network. It is a small way to share the work of keeping DigiByte decentralized.

## What Digi-Mobile is GOOD for
- Using a spare phone/tablet at home to help the DigiByte network as a small node.
- Learning how full nodes work in a hands-on way without buying special hardware.
- Testing a DigiByte wallet that you point your mining rewards to, as long as you remember this is experimental.
- Running a hobby node you do not depend on for anything mission-critical.
- Acting like a tiny personal server for DigiByte—useful for tinkering, not an everyday wallet.

## What Digi-Mobile is NOT for
- **Do not store your life savings or large amounts of DigiByte here.**
- Do not use this as your only backup of keys or recovery phrases.
- Do not use this for institutional custody, business treasury, or regulated financial services.
- Do not expect it to run 24/7 without hiccups—phones die, SD cards fail, background apps get killed.
- Remember: this is experimental software and things can break.

## Important Disclaimers
- **This project is experimental and comes with no guarantees.**
- **Use at your own risk. You are responsible for any funds you choose to store here.**
- **The maintainers and contributors are not liable for lost funds, data, or anything else.**
- Digi-Mobile is a hobby/community tool to help the network, not a commercial product.

## Security & Privacy Basics
- Digi-Mobile should hold only small, replaceable amounts of DigiByte. It is not a primary wallet.
- Losing the phone or installing malicious apps can expose your node or wallet data, especially on rooted devices.
- Running a node shares network traffic with peers, so avoid exposing RPC to the internet unless you know how to secure it.
- Read the full details in [`docs/SECURITY-PRIVACY.md`](./SECURITY-PRIVACY.md).

## How a non-technical user might actually use this
1. You have a spare Android phone with USB debugging enabled.
2. You ask a more technical friend to help. They will:
   - Clone this repository on their computer.
   - Run the one-shot `scripts/quickstart-demo.sh` script.
   - Connect your phone with a USB cable so the script can build, push, and start the node.
3. Once running, the phone quietly helps validate DigiByte blocks and transactions in the background.
4. The technical friend should read the technical docs first and make sure both of you understand the risks.

## If you have a technical friend helping you
- Ask them to plug your device in and run `./setup.sh` from the repository root.
- That wizard auto-detects the device, builds or downloads the binary, and starts the node with friendly prompts.
- You can still follow along in this guide to understand what is happening, but they do the commands for you.

## Concepts in simple terms
- **Full node vs light wallet:** A full node checks the rules itself and shares data with others; a light wallet trusts someone else to do most of the checking.
- **Pruned node:** Keeps recent blockchain data and throws away older blocks locally to save space, while still checking new activity.
- **Why run a node:** More independent nodes mean more resilience and decentralization for the DigiByte network.

## Where to go next
- Start with [`README.md`](../README.md) for the project overview.
- Technical setup lives in [`docs/RUNNING-ON-DEVICE.md`](./RUNNING-ON-DEVICE.md) for device steps.
- Advanced settings are in [`docs/CONFIGURATION.md`](./CONFIGURATION.md).
- If you are not technical, ask a trusted technical helper to walk through those docs with you.
