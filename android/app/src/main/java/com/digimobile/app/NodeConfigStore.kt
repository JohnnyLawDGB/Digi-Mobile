package com.digimobile.app

import android.content.Context
import android.content.SharedPreferences
import com.digimobile.node.NodeConfigOptions
import com.digimobile.node.NodeSetupPreset

class NodeConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(options: NodeConfigOptions) {
        prefs.edit()
            .putString(KEY_PRESET, options.preset.name)
            .putInt(KEY_MAX_CONNECTIONS, options.maxConnections)
            .putInt(KEY_PRUNE_MB, options.pruneTargetMb)
            .putInt(KEY_DBCACHE_MB, options.dbCacheMb)
            .putBoolean(KEY_BLOCKS_ONLY, options.blocksonly)
            .putBoolean(KEY_TELEMETRY_CONSENT, options.telemetryConsent)
            .putBoolean(KEY_WIFI_ONLY, options.wifiOnlyPreference)
            .apply()
    }

    fun load(): NodeConfigOptions {
        val presetName = prefs.getString(KEY_PRESET, NodeSetupPreset.BALANCED.name)
        val preset = runCatching { NodeSetupPreset.valueOf(presetName ?: "") }
            .getOrDefault(NodeSetupPreset.BALANCED)

        return NodeConfigOptions(
            preset = preset,
            maxConnections = prefs.getInt(KEY_MAX_CONNECTIONS, defaultFor(preset).maxConnections),
            pruneTargetMb = prefs.getInt(KEY_PRUNE_MB, defaultFor(preset).pruneTargetMb),
            dbCacheMb = prefs.getInt(KEY_DBCACHE_MB, defaultFor(preset).dbCacheMb),
            blocksonly = prefs.getBoolean(KEY_BLOCKS_ONLY, false),
            telemetryConsent = prefs.getBoolean(KEY_TELEMETRY_CONSENT, false),
            wifiOnlyPreference = prefs.getBoolean(KEY_WIFI_ONLY, true),
        )
    }

    fun defaultFor(preset: NodeSetupPreset): NodeConfigOptions {
        return when (preset) {
            NodeSetupPreset.LIGHT -> NodeConfigOptions(
                preset = preset,
                maxConnections = 8,
                pruneTargetMb = 3_072,
                dbCacheMb = 160,
                blocksonly = true,
                wifiOnlyPreference = true,
            )
            NodeSetupPreset.BALANCED -> NodeConfigOptions(
                preset = preset,
                maxConnections = 12,
                pruneTargetMb = 4_096,
                dbCacheMb = 256,
                blocksonly = false,
                wifiOnlyPreference = true,
            )
            NodeSetupPreset.FULLISH -> NodeConfigOptions(
                preset = preset,
                maxConnections = 16,
                pruneTargetMb = 8_192,
                dbCacheMb = 512,
                blocksonly = false,
                wifiOnlyPreference = false,
            )
            NodeSetupPreset.CUSTOM -> NodeConfigOptions(preset = preset)
        }
    }

    companion object {
        private const val PREFS_NAME = "digimobile_prefs"
        private const val KEY_PRESET = "config_preset"
        private const val KEY_MAX_CONNECTIONS = "config_max_connections"
        private const val KEY_PRUNE_MB = "config_prune_mb"
        private const val KEY_DBCACHE_MB = "config_dbcache_mb"
        private const val KEY_BLOCKS_ONLY = "config_blocksonly"
        private const val KEY_TELEMETRY_CONSENT = "telemetry_consent"
        private const val KEY_WIFI_ONLY = "wifi_only"
    }
}
