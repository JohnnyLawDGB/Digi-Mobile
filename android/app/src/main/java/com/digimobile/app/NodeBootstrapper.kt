package com.digimobile.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.digimobile.node.DigiConfigTemplate
import com.digimobile.node.NodeEnvironment
import com.digimobile.node.RpcCredentials
import java.io.File

// Relevant config handling:
// - NodeBootstrapper.ensureBootstrap(): prepares directories, ensures digibyte.conf, and records RPC credentials.
// - DigiConfigTemplate.ensureConfig(): creates or reads the default mobile digibyte.conf.
// - DigiMobileNodeController.startNode() / nativeStartNode(): pass -conf/-datadir to digibyted.
class NodeBootstrapper(private val context: Context) {

    data class NodePaths(
        val configFile: File,
        val dataDir: File,
        val debugLogFile: File
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFirstRun(): Boolean = !prefs.getBoolean(KEY_BOOTSTRAP_COMPLETE, false)

    fun ensureBootstrap(): NodePaths {
        val dataDir = File(context.filesDir, "digibyte")
        val binDir = File(context.filesDir, "bin")

        dataDir.mkdirs()
        binDir.mkdirs()

        val debugLogFile = File(dataDir, "debug.log")
        val configFile = File(dataDir, "digibyte.conf")

        val credentials = ensureConfig(dataDir)

        ensureCliBinary()

        prefs.edit().putBoolean(KEY_BOOTSTRAP_COMPLETE, true).apply()
        val paths = NodePaths(configFile, dataDir, debugLogFile)
        NodeEnvironment.update(paths, credentials)
        if (!pathsLogged) {
            Log.i(TAG, "Using DigiByte data directory at ${dataDir.absolutePath}")
            Log.i(TAG, "Using DigiByte config at ${configFile.absolutePath}")
            pathsLogged = true
        }
        return paths
    }

    fun ensureCliBinary(): File? {
        val binDir = File(context.filesDir, "bin")
        binDir.mkdirs()

        val cliBinary = File(binDir, "digibyte-cli")
        if (cliBinary.exists()) {
            cliBinary.setExecutable(true, /* ownerOnly= */ true)
            return cliBinary
        }

        return try {
            context.assets.open(CLI_ASSET_PATH).use { input ->
                cliBinary.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            cliBinary.setExecutable(true, /* ownerOnly= */ true)
            cliBinary
        } catch (e: Exception) {
            if (!cliMissingLogged) {
                Log.w(
                    TAG,
                    "digibyte-cli asset missing; TODO: add digibyte-cli to assets/bin for console and RPC support."
                )
                cliMissingLogged = true
            }
            null
        }
    }

    private fun ensureConfig(dataDir: File): RpcCredentials {
        return DigiConfigTemplate.ensureConfig(dataDir)
    }

    companion object {
        private const val TAG = "NodeBootstrapper"
        private const val PREFS_NAME = "digimobile_prefs"
        private const val KEY_BOOTSTRAP_COMPLETE = "bootstrap_complete"
        private const val CLI_ASSET_PATH = "bin/digibyte-cli-arm64"

        /**
         * The digibyte-cli binary must be staged under assets/bin for installation.
         */
        private var cliMissingLogged: Boolean = false
        private var pathsLogged: Boolean = false
    }
}
