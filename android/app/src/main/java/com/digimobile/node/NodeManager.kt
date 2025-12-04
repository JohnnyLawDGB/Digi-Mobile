package com.digimobile.node

import android.content.Context
import com.digimobile.app.NodeBootstrapper
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

class NodeManager(
    private val context: Context,
    private val bootstrapper: NodeBootstrapper,
    private val controller: DigiMobileNodeController,
    private val scope: CoroutineScope
) {

    private val _nodeState = MutableStateFlow<NodeState>(NodeState.Idle)
    val nodeState: StateFlow<NodeState> get() = _nodeState

    private val _logLines = MutableSharedFlow<String>(
        extraBufferCapacity = 100,
        replay = 0
    )
    val logLines: SharedFlow<String> = _logLines.asSharedFlow()

    fun startNode(): Job = scope.launch(Dispatchers.IO) {
        try {
            updateState(NodeState.PreparingEnvironment, "Preparing environment…")
            val paths = bootstrapper.ensureBootstrap()

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
        var progress = 0
        var currentHeight = 0L
        val targetHeight = 100L

        while (true) {
            val status = runCatching { controller.getStatus() }.getOrNull()
            if (status != null) {
                when {
                    status.equals("RUNNING", ignoreCase = true) && progress >= 100 -> {
                        updateState(NodeState.Ready, "Node is ready")
                        return
                    }
                    status.equals("ERROR", ignoreCase = true) -> {
                        updateState(NodeState.Error("Node reported ERROR status"), "Node reported ERROR status")
                        return
                    }
                }
            }

            progress = (progress + 10).coerceAtMost(100)
            currentHeight = (currentHeight + 1).coerceAtMost(targetHeight)
            updateState(
                NodeState.Syncing(progress, currentHeight, targetHeight),
                "Syncing: height $currentHeight / $targetHeight ($progress%)"
            )

            if (progress >= 100) {
                updateState(NodeState.Ready, "Node is ready")
                return
            }

            delay(1_000)
        }
    }

    fun appendLog(message: String) {
        _logLines.tryEmit(message)
    }

    private fun updateState(state: NodeState, logMessage: String) {
        _nodeState.value = state
        appendLog(logMessage)
    }
}
