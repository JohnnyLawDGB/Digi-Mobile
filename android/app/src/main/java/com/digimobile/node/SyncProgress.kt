package com.digimobile.node

/**
 * Represents the node's sync progress as reported by getblockchaininfo.
 * verificationprogress is preferred over header heuristics because it reflects
 * actual validation progress and avoids over/under-reporting when peers lag.
 */
data class SyncProgress(
    val fraction: Float,
    val isInitialDownload: Boolean
) {
    fun toProgressInt(max: Int = 100): Int = (fraction * max).toInt().coerceIn(0, max)
}
