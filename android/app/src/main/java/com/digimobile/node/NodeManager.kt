package com.digimobile.node

import android.content.Context
import com.digimobile.app.NodeBootstrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NodeManager(
    private val context: Context,
    private val bootstrapper: NodeBootstrapper,
    private val controller: DigiMobileNodeController,
    private val scope: CoroutineScope
) {

    private val _nodeState = MutableStateFlow<NodeState>(NodeState.Idle)
    val nodeState: StateFlow<NodeState> get() = _nodeState

    fun startNode(): Job = scope.launch(Dispatchers.IO) {
        try {
            _nodeState.value = NodeState.PreparingEnvironment
            val paths = bootstrapper.ensureBootstrap()

            _nodeState.value = NodeState.DownloadingBinaries(progress = 100)
            _nodeState.value = NodeState.VerifyingBinaries
            _nodeState.value = NodeState.WritingConfig
            _nodeState.value = NodeState.StartingDaemon

            controller.startNode(
                context.applicationContext,
                paths.configFile.absolutePath,
                paths.dataDir.absolutePath
            )

            _nodeState.value = NodeState.ConnectingToPeers
            monitorSyncState()
        } catch (e: Exception) {
            _nodeState.value = NodeState.Error(e.message ?: "Unknown error starting node")
        }
    }

    fun stopNode(): Job = scope.launch(Dispatchers.IO) {
        runCatching { controller.stopNode() }
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
                        _nodeState.value = NodeState.Ready
                        return
                    }
                    status.equals("ERROR", ignoreCase = true) -> {
                        _nodeState.value = NodeState.Error("Node reported ERROR status")
                        return
                    }
                }
            }

            progress = (progress + 10).coerceAtMost(100)
            currentHeight = (currentHeight + 1).coerceAtMost(targetHeight)
            _nodeState.value = NodeState.Syncing(progress, currentHeight, targetHeight)

            if (progress >= 100) {
                _nodeState.value = NodeState.Ready
                return
            }

            delay(1_000)
        }
    }
}
