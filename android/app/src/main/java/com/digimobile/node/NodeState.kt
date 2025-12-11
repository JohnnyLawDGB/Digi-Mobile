package com.digimobile.node

import android.content.Context

sealed class NodeState {
    data object Idle : NodeState()
    data object PreparingEnvironment : NodeState()
    data class DownloadingBinaries(val progress: Int) : NodeState()
    data object VerifyingBinaries : NodeState()
    data object WritingConfig : NodeState()
    data object StartingDaemon : NodeState()
    data class ApplyingSnapshot(
        val phase: SnapshotPhase,
        val progress: Int? // null for indeterminate phases like verification
    ) : NodeState()
    data class StartingUp(val reason: String) : NodeState()
    data object ConnectingToPeers : NodeState()
    data class Syncing(
        val currentHeight: Long?,
        val headerHeight: Long?,
        val progress: SyncProgress,
        val peerCount: Int?
    ) : NodeState()
    data object Ready : NodeState()
    data class Error(val message: String) : NodeState()
}

sealed interface SnapshotPhase {
    data object Download : SnapshotPhase
    data object Verify : SnapshotPhase
    data object Extract : SnapshotPhase
}

@Suppress("UNUSED_PARAMETER")
fun NodeState.toUserMessage(context: Context): String = when (this) {
    NodeState.Idle -> "Node is not running."
    NodeState.PreparingEnvironment -> "Preparing storage and configuration…"
    is NodeState.DownloadingBinaries -> "Downloading DigiByte node binaries (${progress}%)…"
    NodeState.VerifyingBinaries -> "Verifying downloaded files…"
    NodeState.WritingConfig -> "Writing DigiByte configuration…"
    NodeState.StartingDaemon -> "Starting DigiByte node process…"
    is NodeState.ApplyingSnapshot -> when (phase) {
        SnapshotPhase.Download -> "Downloading bootstrap snapshot (${progress}%)…"
        SnapshotPhase.Verify -> "Verifying bootstrap snapshot…"
        SnapshotPhase.Extract -> "Extracting bootstrap snapshot (${progress}%)…"
    }
    is NodeState.StartingUp -> "Node starting… ${reason.ifBlank { "this can take a few minutes" }}"
    NodeState.ConnectingToPeers -> "Node is running; waiting for peer connections and sync details…"
    is NodeState.Syncing -> {
        val hasDetails = currentHeight != null && headerHeight != null
        val percent = progress.toProgressInt()
        val peersText = peerCount?.let { " with $it peers" } ?: ""

        if (hasDetails) {
            "Syncing: height ${currentHeight} / ${headerHeight} (~${percent}%$peersText)"
        } else {
            "Node is running; waiting for sync details (digibyte-cli not available in this build)."
        }
    }
    NodeState.Ready -> "Node is fully synced and ready."
    is NodeState.Error -> "Something went wrong. See details below."
}
