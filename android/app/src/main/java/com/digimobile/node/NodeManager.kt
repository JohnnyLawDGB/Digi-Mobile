package com.digimobile.node

import android.content.Context
import com.digimobile.app.NodeBootstrapper
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

class NodeManager(
    private val context: Context,
    private val bootstrapper: NodeBootstrapper,
    private val controller: DigiMobileNodeController,
    private val scope: CoroutineScope
) {

    private val _nodeState = MutableStateFlow<NodeState>(NodeState.Idle)
    val nodeState: StateFlow<NodeState> get() = _nodeState

    private var lastNodePaths: NodeBootstrapper.NodePaths? = null
    private var cliAvailability: CliAvailability = CliAvailability.Unknown

    private val _logLines = MutableSharedFlow<String>(
        extraBufferCapacity = 100,
        replay = 0
    )
    val logLines: SharedFlow<String> = _logLines.asSharedFlow()

    fun startNode(): Job = scope.launch(Dispatchers.IO) {
        try {
            updateState(NodeState.PreparingEnvironment, "Preparing environment…")
            val paths = bootstrapper.ensureBootstrap()
            lastNodePaths = paths
            ensureCliAvailable()

            updateState(NodeState.DownloadingBinaries(progress = 100), "Downloading binaries (100%)…")
            updateState(NodeState.VerifyingBinaries, "Verifying binaries…")
            updateState(NodeState.WritingConfig, "Writing configuration…")
            updateState(NodeState.StartingDaemon, "Starting DigiByte daemon…")

            controller.startNode(
                context.applicationContext,
                paths.configFile.absolutePath,
                paths.dataDir.absolutePath
            )

            updateState(NodeState.ConnectingToPeers, "Connecting to peers…")
            monitorSyncState()
        } catch (e: Exception) {
            val message = e.message ?: "Unknown error starting node"
            appendLog("Error: $message")
            _nodeState.value = NodeState.Error(message)
        }
    }

    suspend fun stopNode() = withContext(Dispatchers.IO) {
        val paths = lastNodePaths ?: runCatching { bootstrapper.ensureBootstrap() }.getOrNull()
        if (paths == null) {
            val message = "Cannot stop node without configuration paths"
            appendLog("Error: $message")
            _nodeState.value = NodeState.Error(message)
            return@withContext
        }

        if (!ensureCliAvailable()) {
            val message = "digibyte-cli is not available; cannot send stop command"
            appendLog("Error: $message")
            _nodeState.value = NodeState.Error(message)
            return@withContext
        }

        val cliBinary = File(context.filesDir, "bin/digibyte-cli")

        try {
            appendLog("Stopping DigiByte daemon…")
            val stopProcess = ProcessBuilder(
                cliBinary.absolutePath,
                "-conf=${paths.configFile.absolutePath}",
                "-datadir=${paths.dataDir.absolutePath}",
                "stop"
            )
                .redirectErrorStream(true)
                .start()

            val output = stopProcess.inputStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = stopProcess.waitFor()
            if (exitCode != 0) {
                throw IllegalStateException("digibyte-cli stop failed with exit $exitCode: $output")
            }

            if (output.isNotEmpty()) {
                appendLog(output)
            }

            waitForShutdown()
        } catch (e: Exception) {
            val message = e.message ?: "Unknown error stopping node"
            appendLog("Error: $message")
            _nodeState.value = NodeState.Error(message)
        }
    }

    private suspend fun waitForShutdown() {
        val deadline = System.currentTimeMillis() + STOP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val status = runCatching { controller.getStatus() }.getOrNull()
            val isRunning = status?.equals("RUNNING", ignoreCase = true) == true
            if (!isRunning) {
                updateState(NodeState.Idle, "Node stopped")
                return
            }
            delay(STOP_POLL_DELAY_MS)
        }

        val message = "Node did not stop within ${STOP_TIMEOUT_MS / 1000} seconds"
        appendLog("Error: $message")
        _nodeState.value = NodeState.Error(message)
    }

    private suspend fun monitorSyncState() {
        val paths = lastNodePaths
        if (paths == null) {
            appendLog("Cannot monitor sync without node paths")
            _nodeState.value = NodeState.Error("Missing node paths")
            return
        }

        while (true) {
            val status = runCatching { controller.getStatus() }.getOrNull()
            if (status != null) {
                when {
                    status.equals("ERROR", ignoreCase = true) -> {
                        updateState(NodeState.Error("Node reported ERROR status"), "Node reported ERROR status")
                        return
                    }
                    status.equals("NOT_RUNNING", ignoreCase = true) -> {
                        updateState(NodeState.Error("Node stopped unexpectedly"), "Node stopped unexpectedly")
                        return
                    }
                }
            }

            val cliAvailable = ensureCliAvailable()
            val blockchainInfo = if (cliAvailable) queryBlockchainInfo(paths) else null
            val targetHeight = blockchainInfo?.targetHeight
            val currentHeight = blockchainInfo?.blocks
            val progress = blockchainInfo?.progressPercent()

            val isSynced = blockchainInfo?.let { info ->
                info.progressPercent() >= READY_THRESHOLD_PERCENT ||
                    info.headers - info.blocks <= SYNC_TOLERANCE
            } == true

            val nextState = if (isSynced) {
                NodeState.Ready
            } else {
                NodeState.Syncing(progress?.roundToInt(), currentHeight, targetHeight)
            }

            val currentState = _nodeState.value
            if (currentState != nextState) {
                val logMessage = if (isSynced) {
                    "Node is fully synced and ready"
                } else {
                    formatSyncLog(currentHeight, targetHeight, progress)
                }
                updateState(nextState, logMessage)
            } else if (nextState is NodeState.Syncing && currentState is NodeState.Syncing) {
                if (currentState.progress != nextState.progress ||
                    currentState.currentHeight != nextState.currentHeight ||
                    currentState.targetHeight != nextState.targetHeight
                ) {
                    updateState(nextState, formatSyncLog(currentHeight, targetHeight, progress))
                }
            }

            delay(5_000)
        }
    }

    private fun queryBlockchainInfo(paths: NodeBootstrapper.NodePaths): BlockchainInfo? {
        return runCatching {
            val cliBinary = File(context.filesDir, "bin/digibyte-cli")
            if (!cliBinary.exists()) return null
            val confArg = "-conf=${paths.configFile.absolutePath}"
            val datadirArg = "-datadir=${paths.dataDir.absolutePath}"
            val process = ProcessBuilder(
                cliBinary.absolutePath,
                confArg,
                datadirArg,
                "getblockchaininfo"
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                appendLog("digibyte-cli getblockchaininfo failed with exit $exitCode")
                return null
            }

            val json = JSONObject(output)
            val blocks = json.optLong("blocks", 0L)
            val headers = json.optLong("headers", blocks)
            val verificationProgress = json.optDouble("verificationprogress", 0.0)
            BlockchainInfo(blocks, headers, verificationProgress)
        }.getOrElse { error ->
            appendLog("Failed to query blockchain info: ${error.message}")
            null
        }
    }

    private data class BlockchainInfo(
        val blocks: Long,
        val headers: Long,
        val verificationProgress: Double,
    ) {
        val targetHeight: Long = maxOf(blocks, headers)

        fun progressPercent(): Double {
            val progressFromVerification = (verificationProgress * 100).coerceAtMost(100.0)
            if (progressFromVerification > 0.0) {
                return progressFromVerification
            }
            if (targetHeight <= 0) return 0.0
            return ((blocks.toDouble() / targetHeight) * 100).coerceAtMost(100.0)
        }
    }

    companion object {
        private const val SYNC_TOLERANCE = 2L
        private const val READY_THRESHOLD_PERCENT = 99.9
        private const val STOP_TIMEOUT_MS = 30_000L
        private const val STOP_POLL_DELAY_MS = 1_000L
    }

    private fun ensureCliAvailable(): Boolean {
        if (cliAvailability == CliAvailability.Available) return true
        if (cliAvailability == CliAvailability.Missing) return false

        val cliBinary = bootstrapper.ensureCliBinary()
        val isPresent = cliBinary?.exists() == true
        if (isPresent) {
            cliAvailability = CliAvailability.Available
            return true
        }

        appendLog("digibyte-cli is not available in this build; falling back to basic sync state.")
        cliAvailability = CliAvailability.Missing
        return false
    }

    private fun formatSyncLog(currentHeight: Long?, targetHeight: Long?, progress: Double?): String {
        val progressText = progress?.roundToInt()?.let { "$it%" } ?: "progress unknown"
        val heightText = if (currentHeight != null && targetHeight != null) {
            "$currentHeight / $targetHeight"
        } else {
            "height unknown"
        }
        return "Syncing: height $heightText ($progressText)"
    }

    private enum class CliAvailability {
        Unknown,
        Available,
        Missing
    }

    fun appendLog(message: String) {
        _logLines.tryEmit(message)
    }

    private fun updateState(state: NodeState, logMessage: String) {
        _nodeState.value = state
        appendLog(logMessage)
    }
}
