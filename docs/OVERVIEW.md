# Digi-Mobile overview

Digi-Mobile packages DigiByte Core for Android builds and related tooling. The project keeps a pinned DigiByte Core release alongside Android-specific build scripts and configuration.

## Key directories

- `core/`: DigiByte Core source tracked as a submodule pinned to v8.26.
- `android/`: Android build glue, patches, and packaging assets.
- `scripts/`: Helper scripts for fetching core sources, printing the repository layout, and building artifacts.
- `config/`: Configuration files and pinned version references.
- `docs/`: Project documentation for Android builds, setup steps, and repository orientation.

## Getting Started

- Initialize the DigiByte Core submodule following [`docs/CORE-SETUP.md`](./CORE-SETUP.md).
- Run the helper script [`scripts/setup-core.sh`](../scripts/setup-core.sh) to ensure the `core/` tree is initialized and on the expected tag.
