package com.digimobile.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File

class NodeBootstrapper(private val context: Context) {

    data class NodePaths(
        val configFile: File,
        val dataDir: File,
        val logsDir: File
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFirstRun(): Boolean = !prefs.getBoolean(KEY_BOOTSTRAP_COMPLETE, false)

    fun ensureBootstrap(): NodePaths {
        val configDir = context.getDir("config", Context.MODE_PRIVATE)
        val dataDir = context.getDir("data", Context.MODE_PRIVATE)
        val logsDir = context.getDir("logs", Context.MODE_PRIVATE)

        configDir.mkdirs()
        dataDir.mkdirs()
        logsDir.mkdirs()

        val configFile = File(configDir, "digimobile-pruned.conf")
        if (!configFile.exists()) {
            copyDefaultConfig(configFile, dataDir, logsDir)
        }

        prefs.edit().putBoolean(KEY_BOOTSTRAP_COMPLETE, true).apply()
        return NodePaths(configFile, dataDir, logsDir)
    }

    private fun copyDefaultConfig(configFile: File, dataDir: File, logsDir: File) {
        val logFile = File(logsDir, "debug.log")
        val builder = StringBuilder()
        try {
            context.assets.open(DEFAULT_CONFIG_ASSET).bufferedReader().use { reader ->
                builder.append(reader.readText())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falling back to generated default config: ${e.message}")
            builder.append(buildDefaultConfigBody())
        }

        val body = builder.toString()
        val config = StringBuilder(body)
        if (!body.contains("datadir=")) {
            config.append("\ndatadir=${dataDir.absolutePath}")
        }
        if (!body.contains("debuglogfile=")) {
            config.append("\ndebuglogfile=${logFile.absolutePath}")
        }

        configFile.writeText(config.toString().trim() + "\n")
    }

    private fun buildDefaultConfigBody(): String {
        return """
            # Digi-Mobile default pruned configuration
            prune=550
            server=1
            daemon=0
            listen=1
            txindex=0
            rpcallowip=127.0.0.1
            rpcbind=127.0.0.1
        """.trimIndent()
    }

    companion object {
        private const val TAG = "NodeBootstrapper"
        private const val PREFS_NAME = "digimobile_prefs"
        private const val KEY_BOOTSTRAP_COMPLETE = "bootstrap_complete"
        private const val DEFAULT_CONFIG_ASSET = "config/digimobile-pruned.conf"
    }
}
