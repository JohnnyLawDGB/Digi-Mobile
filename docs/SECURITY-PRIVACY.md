# Digi-Mobile Security & Privacy Notes

## Overview
- Digi-Mobile runs a pruned DigiByte full node on an Android device. It keeps consensus behavior intact while discarding older blocks to save space.
- The app can optionally store private keys if you enable wallet functionality.
- Phones and tablets are consumer devices, not dedicated security hardware. Treat them accordingly.

## Key Security Points
- **Do not store large or irreplaceable amounts of DigiByte with Digi-Mobile.**
- **Treat Digi-Mobile as a hobby/test setup, not as your primary wallet.**
- Device risks:
  - If your phone is lost or stolen, someone else may access your node or wallet if they bypass OS security.
  - Malicious apps on the same device may try to read data directories if the OS is compromised or rooted.
- Upgrade risks:
  - Experimental builds can have bugs that lead to data loss or corruption.
  - Always back up wallet seeds/keys using other secure methods if you choose to use the wallet.

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
- Digi-Mobile is okay as a small wallet to receive mining payouts or small test amounts.
- It is not recommended as your main long-term savings wallet.
- Always back up keys/seeds outside the device, and prefer hardware wallets or better-audited software for significant balances.

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
