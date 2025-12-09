# Digi-Mobile Security & Privacy Notes

> **THIS IS HIGHLY EXPERIMENTAL SOFTWARE. DO NOT INSTALL ON ANYTHING OTHER THAN SPARE HARDWARE YOU WOULDN’T MIND BRICKING.**

## Overview
- Digi-Mobile runs a pruned DigiByte relay node on an Android device. It keeps consensus behavior intact while discarding older blocks to save space.
- Wallet functionality is **not present** in this pre-release; console/RPC access remains available for advanced users.
- Phones and tablets are consumer devices, not dedicated security hardware. Treat them accordingly.

## Key Security Points
- **Do not store irreplaceable data on this device. Use only spare hardware.**
- Device risks:
  - If your phone is lost or stolen, someone else may access node data if they bypass OS security.
  - Malicious apps on the same device may try to read data directories if the OS is compromised or rooted.
- Upgrade risks:
  - Experimental builds can have bugs that lead to data loss or corruption.

## Privacy Considerations
- Running a node means your device connects to peers and shares transaction/block data to help the network.
- Your IP address is visible to peers, and logs may contain network-related information.
- If you use RPC or remote access, exposing it insecurely can leak sensitive information.
- Recommendations:
  - Prefer the default, local-only RPC settings.
  - Do not expose RPC to the internet unless you understand the risks and secure it properly.
  - Avoid using Digi-Mobile on highly sensitive networks if you are uncomfortable with P2P traffic.

## Pruned Node vs Full Node
- Pruned nodes keep recent blockchain history and discard older data while still fully validating new blocks and transactions.
- Pruning does not change the consensus or validation security model.
- Pruning does affect:
  - How far back you can rescan locally.
  - How much historical data remains on your device.

## Wallet Usage Guidance
- This pre-release does **not** include wallet functionality. Future work may add minimal wallet support; today this is a relay node only.

## Threat Model (High Level)
- Example threats:
  - Physical theft of the device.
  - Malware on the device.
  - OS bugs or rooting/jailbreaking.
  - Compromised APK if downloaded from unofficial sources.
- Mitigations:
  - Use device PIN/biometrics.
  - Only install APKs from this official GitHub repository.
  - Keep the OS up to date.
  - Do not sideload random "Digi-Mobile" apps from unknown websites.

## Summary
- Digi-Mobile is a great way to help the network and experiment.
- It is not a bank vault.
- You are responsible for managing your own risk.
