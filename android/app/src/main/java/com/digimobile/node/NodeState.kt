package com.digimobile.node

import android.content.Context
import kotlin.math.roundToInt

sealed class NodeState {
    data object Idle : NodeState()
    data object PreparingEnvironment : NodeState()
    data class DownloadingBinaries(val progress: Int) : NodeState()
    data object VerifyingBinaries : NodeState()
    data object WritingConfig : NodeState()
    data object StartingDaemon : NodeState()
    data object ConnectingToPeers : NodeState()
    data class Syncing(
        val currentHeight: Long?,
        val headerHeight: Long?,
        val progress: Double?,
        val peerCount: Int?
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
    NodeState.ConnectingToPeers -> "Node is running; waiting for peer connections and sync details…"
    is NodeState.Syncing -> {
        val hasDetails = currentHeight != null && headerHeight != null && progress != null
        val percent = progress?.let { (it * 100).roundToInt().coerceIn(0, 100) }
        val peersText = peerCount?.let { " with $it peers" } ?: ""

        if (hasDetails && percent != null) {
            "Syncing: height ${currentHeight} / ${headerHeight} (~${percent}%$peersText)"
        } else {
            "Node is running; waiting for sync details (digibyte-cli not available in this build)."
        }
    }
    NodeState.Ready -> "Node is fully synced and ready."
    is NodeState.Error -> "Something went wrong. See details below."
}
