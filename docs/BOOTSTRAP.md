# Snapshot Bootstrap Process

This document explains how DigiByte Mobile loads a prebuilt chainstate snapshot during first-time startup.

## Snapshot File Placement

The app looks for the snapshot archive at startup before falling back to a full sync. It checks two locations in order:

1. **External app storage**: `Android/data/com.digimobile.app/files/` (returned by `getExternalFilesDir(null)`).
2. **Internal app storage**: the app-private files directory (`context.filesDir`).

Place the snapshot tarball in the external location to make it discoverable without rooting the device; if it is absent there, the app uses the internal copy instead. The expected filename is defined by `SNAPSHOT_FILENAME`.

## Verification Steps

1. **SHA-256 checksum**: The app computes the SHA-256 digest of the snapshot file and compares it to `SNAPSHOT_SHA256`. Mismatches abort the bootstrap and surface an error in the log.
2. **Tarball extraction**: When the checksum matches, the `chainstate/` directory inside the archive is extracted into the node datadir.
3. **Block header check**: After the node starts, the app runs `getblockhash <SNAPSHOT_HEIGHT>` and compares the result to `SNAPSHOT_HASH`. If the header does not match, the chainstate is deleted and the user is prompted to restart for a full sync.

## Updating Snapshot Constants

When publishing a new snapshot, update the constants in `android/app/src/main/java/com/digimobile/app/ChainstateBootstrapper.kt`:

- `SNAPSHOT_FILENAME`: archive name (e.g., `dgb-chainstate-mainnet-h<height>-<version>.tar.gz`).
- `SNAPSHOT_SHA256`: SHA-256 of the archive, computed with `sha256sum <file>`.
- `SNAPSHOT_HEIGHT`: block height the snapshot represents.
- `SNAPSHOT_HASH`: block hash at `SNAPSHOT_HEIGHT` (obtain via `digibyte-cli getblockhash <height>` on a synced node).

After updating these values and shipping the new snapshot, the app will automatically verify and apply it on next startup.
