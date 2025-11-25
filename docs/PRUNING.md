# Pruned node defaults for Android

Digi-Mobile targets pruned operation to minimize storage. Key guidelines:

- Default pruning target: set `prune=2048` (MiB) or higher in `config/android-pruned.conf` for safer reorg protection than the minimum 550 MiB.
- Keep `txindex=0` (default) to avoid full transaction index overhead.
- Consider lowering connection counts (`maxconnections=8-12`) to reduce bandwidth and battery usage.
- Adjust `dbcache` to fit device RAM (e.g., 100–300 MB). Lower values reduce memory footprint at the cost of sync speed.
- Disable services that add dependencies or power drain unless needed: `upnp=0`, `natpmp=0`, `listenonion=0`.
- Prefer storing data under app-managed directories (e.g., `/data/data/<app>/files/.digibyte`) with proper permissions.
- For log growth, configure log rotation externally or use `maxmempool`/`maxorphantx` defaults to limit transient memory.
