package com.digimobile.node

data class NodeConfigOptions(
    val preset: NodeSetupPreset = NodeSetupPreset.BALANCED,
    val maxConnections: Int = 10,
    val pruneTargetMb: Int = 4096,
    val dbCacheMb: Int = 256,
    val blocksonly: Boolean = false,
    val telemetryConsent: Boolean = false,
    val wifiOnlyPreference: Boolean = true,
)

enum class NodeSetupPreset {
    LIGHT,
    BALANCED,
    FULLISH,
    CUSTOM,
}
