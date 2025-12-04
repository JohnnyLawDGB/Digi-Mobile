package com.digimobile.node

sealed class NodeState {
    data object Idle : NodeState()
    data object PreparingEnvironment : NodeState()
    data class DownloadingBinaries(val progress: Int) : NodeState()
    data object VerifyingBinaries : NodeState()
    data object WritingConfig : NodeState()
    data object StartingDaemon : NodeState()
    data object ConnectingToPeers : NodeState()
    data class Syncing(
        val progress: Int,
        val currentHeight: Long,
        val targetHeight: Long
    ) : NodeState()
    data object Ready : NodeState()
    data class Error(val message: String) : NodeState()
}
