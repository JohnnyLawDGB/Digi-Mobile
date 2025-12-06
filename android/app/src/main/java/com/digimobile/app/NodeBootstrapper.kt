package com.digimobile.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.digimobile.node.DigiConfigTemplate
import com.digimobile.node.NodeEnvironment
import com.digimobile.node.RpcCredentials
import java.io.File
import java.io.FileOutputStream

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
        val binDir = File(dataDir.parentFile, "bin")

        dataDir.mkdirs()
        binDir.mkdirs()

        installBundledBinaries(context)

        val debugLogFile = File(dataDir, "debug.log")
        val configFile = File(dataDir, "digibyte.conf")

        ensureCliBinary()

        val credentials = ensureConfig(dataDir)

        prefs.edit().putBoolean(KEY_BOOTSTRAP_COMPLETE, true).apply()
        val paths = NodePaths(configFile, dataDir, debugLogFile)
        NodeEnvironment.update(paths, credentials)
        if (!pathsLogged) {
            Log.i(
                TAG,
                "Using DigiByte datadir=${dataDir.absolutePath} conf=${configFile.absolutePath}"
            )
            pathsLogged = true
        }
        return paths
    }

    fun ensureCliBinary(): File? {
        val binDir = File(context.filesDir, "bin")

        return try {
            installBundledBinaries(context)
            val cliBinary = File(binDir, "digibyte-cli")
            cliBinary.setExecutable(true, /* ownerOnly= */ false)
            cliBinary
        } catch (e: Exception) {
            if (!cliMissingLogged) {
                Log.w(
                    TAG,
                    "digibyte-cli asset missing; CLI features will be disabled.",
                    e
                )
                cliMissingLogged = true
            }
            null
        }
    }

    private data class BinaryAssets(
        val daemonAssetName: String,
        val cliAssetName: String
    )

    private fun selectBinaryAssetsForDevice(): BinaryAssets {
        val abis = Build.SUPPORTED_ABIS.toList()

        return when {
            abis.contains("arm64-v8a") -> {
                BinaryAssets(
                    daemonAssetName = "digibyted-arm64",
                    cliAssetName = "digibyte-cli-arm64"
                )
            }
            abis.contains("armeabi-v7a") -> {
                BinaryAssets(
                    daemonAssetName = "digibyted-armeabi-v7a",
                    cliAssetName = "digibyte-cli-armeabi-v7a"
                )
            }
            else -> {
                throw IllegalStateException("Unsupported ABI: $abis")
            }
        }
    }

    private fun stageNodeBinaries(context: Context) {
        val (daemonAssetName, cliAssetName) = selectBinaryAssetsForDevice()
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }

        fun copyAsset(assetName: String, destName: String) {
            val destFile = File(binDir, destName)
            if (!destFile.exists()) {
                context.assets.open("bin/$assetName").use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            destFile.setExecutable(true, /* ownerOnly= */ false)
        }

        copyAsset(daemonAssetName, "digibyted")
        copyAsset(cliAssetName, "digibyte-cli")

        android.util.Log.i(
            TAG,
            "Staged binaries for ABI=${Build.SUPPORTED_ABIS.joinToString()} daemon=$daemonAssetName cli=$cliAssetName"
        )
    }

    fun installBundledBinaries(context: Context) {
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }

        fun installIfMissing(assetName: String, destinationName: String) {
            val destination = File(binDir, destinationName)
            if (!destination.exists()) {
                context.assets.open("bin/$assetName").use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            destination.setExecutable(true, /* ownerOnly= */ false)
        }

        installIfMissing("digibyted-arm64", "digibyted")
        installIfMissing("digibyte-cli-arm64", "digibyte-cli")
    }

    fun downloadOrUpdateBinaries(onLog: (String) -> Unit = { Log.i(TAG, it) }): Boolean {
        val abis = Build.SUPPORTED_ABIS.toList()
        if (abis.contains("arm64-v8a")) {
            onLog("Using bundled ARM64 binaries; skipping download")
            return true
        }

        return runCatching {
            stageNodeBinaries(context)
            true
        }.getOrElse { error ->
            onLog("Failed to update binaries: ${error.message}")
            false
        }
    }

    private fun ensureConfig(dataDir: File): RpcCredentials {
        return DigiConfigTemplate.ensureConfig(dataDir)
    }

    companion object {
        private const val TAG = "NodeBootstrapper"
        private const val PREFS_NAME = "digimobile_prefs"
        private const val KEY_BOOTSTRAP_COMPLETE = "bootstrap_complete"

        /**
         * The digibyte-cli binary must be staged under assets/bin for installation.
         */
        private var cliMissingLogged: Boolean = false
        private var pathsLogged: Boolean = false
    }
}
