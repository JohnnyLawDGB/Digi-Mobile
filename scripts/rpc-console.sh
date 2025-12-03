#!/usr/bin/env bash
# Interactive RPC console helper that prompts for a wallet and forwards commands
# to digibyte-cli with the correct -rpcwallet argument. Intended for local
# testing/hobby use.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DIGIBYTE_CLI_DEFAULT="$REPO_ROOT/core/src/digibyte-cli"
DIGIBYTE_CLI="${DIGIBYTE_CLI:-$DIGIBYTE_CLI_DEFAULT}"
CONFIG_FILE="${CONFIG_FILE:-$REPO_ROOT/config/digimobile-pruned.conf}"

log() { echo "[rpc-console] $*"; }
die() { echo "[rpc-console] ERROR: $*" >&2; exit 1; }

if [[ ! -x "$DIGIBYTE_CLI" ]]; then
  die "digibyte-cli missing or not executable at $DIGIBYTE_CLI (override with DIGIBYTE_CLI env var)"
fi

RPC_ARGS=()
[[ -f "$CONFIG_FILE" ]] && RPC_ARGS+=("-conf=$CONFIG_FILE")
[[ -n "${RPC_PORT:-}" ]] && RPC_ARGS+=("-rpcport=$RPC_PORT")
[[ -n "${RPC_CONNECT:-}" ]] && RPC_ARGS+=("-rpcconnect=$RPC_CONNECT")
[[ -n "${RPC_COOKIE_DIR:-}" ]] && RPC_ARGS+=("-datadir=$RPC_COOKIE_DIR")

run_cli() {
  "$DIGIBYTE_CLI" "${RPC_ARGS[@]}" "$@"
}

select_wallet() {
  local wallets_json wallet_names
  if ! wallets_json=$(run_cli listwalletdir 2>/dev/null); then
    log "Could not list wallets via RPC; falling back to blank (node may be offline or wallet disabled)."
    echo ""
    return
  fi

  wallet_names=($(python - <<'PY'
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(1)
wallets = data.get("wallets", [])
for w in wallets:
    name = w.get("name") or w.get("path") or ""
    if name:
        print(name)
PY
<<<"$wallets_json")) || wallet_names=()

  if ((${#wallet_names[@]} == 0)); then
    log "No wallets found; continuing without -rpcwallet."
    echo ""
    return
  fi

  log "Available wallets:"
  local i=1
  for w in "${wallet_names[@]}"; do
    echo "  [$i] $w"
    ((i++))
  done
  echo "  [0] None (call RPC without -rpcwallet)"

  local choice
  while true; do
    read -rp "Select a wallet [0-${#wallet_names[@]}]: " choice
    if [[ "$choice" =~ ^[0-9]+$ ]] && ((choice >=0 && choice <= ${#wallet_names[@]})); then
      break
    fi
    echo "Enter a number between 0 and ${#wallet_names[@]}"
  done

  if ((choice == 0)); then
    echo ""
  else
    echo "${wallet_names[choice-1]}"
  fi
}

wallet=$(select_wallet)
if [[ -n "$wallet" ]]; then
  RPC_ARGS+=("-rpcwallet=$wallet")
  log "Using wallet: $wallet"
else
  log "Proceeding without wallet context."
fi

log "Enter RPC commands (blank line to quit)."
while true; do
  read -rp "digibyte-cli> " -a user_cmd
  (( ${#user_cmd[@]} == 0 )) && break
  if ! run_cli "${user_cmd[@]}"; then
    log "Command failed; check RPC credentials, wallet state, or args."
  fi
done

log "Goodbye."
