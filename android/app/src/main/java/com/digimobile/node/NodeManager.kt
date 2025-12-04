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

    fun stopNode(): Job = scope.launch(Dispatchers.IO) {
        runCatching { controller.stopNode() }
        appendLog("Node stopped")
        _nodeState.value = NodeState.Idle
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

            val blockchainInfo = queryBlockchainInfo(paths)
            val targetHeight = blockchainInfo?.targetHeight ?: 0L
            val currentHeight = blockchainInfo?.blocks ?: 0L
            val progress = blockchainInfo?.progressPercent() ?: 0.0

            val isSynced = blockchainInfo?.let { info ->
                info.progressPercent() >= READY_THRESHOLD_PERCENT ||
                    info.headers - info.blocks <= SYNC_TOLERANCE
            } == true

            val nextState = if (isSynced) {
                NodeState.Ready
            } else {
                NodeState.Syncing(progress.roundToInt(), currentHeight, targetHeight)
            }

            val currentState = _nodeState.value
            if (currentState != nextState) {
                val logMessage = if (isSynced) {
                    "Node is fully synced and ready"
                } else {
                    "Syncing: height $currentHeight / $targetHeight (${progress.roundToInt()}%)"
                }
                updateState(nextState, logMessage)
            } else if (nextState is NodeState.Syncing && currentState is NodeState.Syncing) {
                if (currentState.progress != nextState.progress ||
                    currentState.currentHeight != nextState.currentHeight ||
                    currentState.targetHeight != nextState.targetHeight
                ) {
                    updateState(nextState, "Syncing: height $currentHeight / $targetHeight (${progress.roundToInt()}%)")
                }
            }

            delay(5_000)
        }
    }

    private fun queryBlockchainInfo(paths: NodeBootstrapper.NodePaths): BlockchainInfo? {
        return runCatching {
            val cliBinary = File(context.filesDir, "bin/digibyte-cli")
            if (!cliBinary.exists()) {
                appendLog("digibyte-cli is missing at ${cliBinary.absolutePath}")
                return null
            }
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
    }

    fun appendLog(message: String) {
        _logLines.tryEmit(message)
    }

    private fun updateState(state: NodeState, logMessage: String) {
        _nodeState.value = state
        appendLog(logMessage)
    }
}
