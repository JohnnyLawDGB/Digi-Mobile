package com.digimobile.node

import android.content.Context

sealed class NodeState {
    data object Idle : NodeState()
    data object PreparingEnvironment : NodeState()
    data class DownloadingBinaries(val progress: Int) : NodeState()
    data object VerifyingBinaries : NodeState()
    data object WritingConfig : NodeState()
    data object StartingDaemon : NodeState()
    data object ConnectingToPeers : NodeState()
    data class Syncing(
        val progress: Int?,
        val currentHeight: Long?,
        val targetHeight: Long?
    ) : NodeState()
    data object Ready : NodeState()
    data class Error(val message: String) : NodeState()
}

@Suppress("UNUSED_PARAMETER")
fun NodeState.toUserMessage(context: Context): String = when (this) {
    NodeState.Idle -> "Node is not running."
    NodeState.PreparingEnvironment -> "Preparing storage and configuration…"
    is NodeState.DownloadingBinaries -> "Downloading DigiByte node binaries (${progress}%)…"
    NodeState.VerifyingBinaries -> "Verifying downloaded files…"
    NodeState.WritingConfig -> "Writing DigiByte configuration…"
    NodeState.StartingDaemon -> "Starting DigiByte node process…"
    NodeState.ConnectingToPeers -> "Connecting to peers…"
    is NodeState.Syncing -> {
        val progressText = progress?.let { "$it%" } ?: "in progress"
        val heightText = if (currentHeight != null && targetHeight != null && targetHeight > 0) {
            "${currentHeight} / ${targetHeight}"
        } else {
            null
        }

        if (heightText != null && progress != null) {
            "Syncing blockchain: $heightText ($progressText)…"
        } else if (heightText != null) {
            "Syncing blockchain: $heightText…"
        } else {
            "Syncing blockchain ($progressText)…"
        }
    }
    NodeState.Ready -> "Node is fully synced and ready."
    is NodeState.Error -> "Something went wrong. See details below."
}
