package com.digimobile.node

import android.content.Context
import com.digimobile.app.NodeBootstrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NodeManager(
    private val context: Context,
    private val bootstrapper: NodeBootstrapper = NodeBootstrapper(context),
    private val nodeController: DigiMobileNodeController = DigiMobileNodeController(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val _state = MutableStateFlow<NodeState>(NodeState.Idle)
    val state: StateFlow<NodeState> = _state.asStateFlow()

    fun startNode() = scope.launch {
        try {
            _state.value = NodeState.PreparingEnvironment
            val paths = bootstrapper.ensureBootstrap()

            _state.value = NodeState.WritingConfig
            _state.value = NodeState.VerifyingBinaries
            _state.value = NodeState.StartingDaemon

            nodeController.startNode(
                context,
                paths.configFile.absolutePath,
                paths.dataDir.absolutePath
            )

            _state.value = NodeState.ConnectingToPeers
            _state.value = NodeState.Ready
        } catch (e: Exception) {
            _state.value = NodeState.Error(e.message ?: "Unknown error starting node")
        }
    }

    fun stopNode() = scope.launch {
        runCatching { nodeController.stopNode() }
        _state.value = NodeState.Idle
    }
}
