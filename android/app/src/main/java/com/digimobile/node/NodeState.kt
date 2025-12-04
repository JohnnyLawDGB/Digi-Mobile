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

fun NodeState.toUserMessage(): String = when (this) {
    NodeState.Idle -> "Not running"
    NodeState.PreparingEnvironment -> "Preparing environment..."
    is NodeState.DownloadingBinaries -> "Downloading binaries (${this.progress}%)..."
    NodeState.VerifyingBinaries -> "Verifying binaries..."
    NodeState.WritingConfig -> "Writing configuration..."
    NodeState.StartingDaemon -> "Starting DigiByte daemon..."
    NodeState.ConnectingToPeers -> "Connecting to peers..."
    is NodeState.Syncing -> "Syncing (${this.progress}%) height ${this.currentHeight}/${this.targetHeight}"
    NodeState.Ready -> "Node running"
    is NodeState.Error -> "Error: ${this.message}"
}
