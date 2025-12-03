# RPC console helper (wallet-aware)

The `scripts/rpc-console.sh` helper provides a small interactive wrapper around
`digibyte-cli` that prompts for a wallet before forwarding RPC commands. This
avoids HTTP 500 errors like `Wallet file not specified` when the node requires a
wallet-scoped RPC path.

## Usage

```bash
# Ensure digibyte-cli is built and available at core/src/digibyte-cli
./scripts/rpc-console.sh
```

Environment variables:

- `DIGIBYTE_CLI`: override the path to `digibyte-cli`.
- `CONFIG_FILE`: path to the configuration file containing RPC credentials.
- `RPC_CONNECT` / `RPC_PORT`: override RPC host/port.
- `RPC_COOKIE_DIR`: datadir that holds the `.cookie` file for authentication.

The script will:

1. Fetch the wallet list via `listwalletdir`.
2. Ask which wallet to target (or "None" to call node-level RPCs).
3. Open an interactive prompt that forwards commands with the chosen
   `-rpcwallet` flag when applicable.

Press Enter on a blank line to exit the console.
