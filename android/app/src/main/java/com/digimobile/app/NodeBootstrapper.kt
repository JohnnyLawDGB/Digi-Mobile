package com.digimobile.app

import android.content.Context
import android.content.SharedPreferences
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
            configFile.writeText(buildDefaultConfig(dataDir, logsDir))
        }

        prefs.edit().putBoolean(KEY_BOOTSTRAP_COMPLETE, true).apply()
        return NodePaths(configFile, dataDir, logsDir)
    }

    private fun buildDefaultConfig(dataDir: File, logsDir: File): String {
        val logFile = File(logsDir, "debug.log")
        return """
            # Digi-Mobile default pruned configuration
            # Generated on first run. Edit cautiously if you know what you're doing.
            prune=550
            server=1
            daemon=0
            listen=1
            txindex=0
            rpcallowip=127.0.0.1
            rpcbind=127.0.0.1
            datadir=${dataDir.absolutePath}
            debuglogfile=${logFile.absolutePath}
            # TODO: swap in packaged DigiByte daemon binary path or asset extraction when available.
            """.trimIndent()
    }

    companion object {
        private const val PREFS_NAME = "digimobile_prefs"
        private const val KEY_BOOTSTRAP_COMPLETE = "bootstrap_complete"
    }
}
