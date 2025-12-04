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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var cliAvailability: CliAvailability = CliAvailability.Unknown
    val cliAvailable: Boolean
        get() = evaluateCliAvailability()

    private var cliWarningLogged: Boolean = false

    private val _logLines = MutableSharedFlow<String>(
        extraBufferCapacity = 100,
        replay = 0
    )
    val logLines: SharedFlow<String> = _logLines.asSharedFlow()

    private val startMutex = Mutex()

    fun startNode(): Job = scope.launch(Dispatchers.IO) {
        val canStart = startMutex.withLock {
            val currentState = _nodeState.value
            val blockedClasses = setOf(
                NodeState.PreparingEnvironment::class,
                NodeState.DownloadingBinaries::class,
                NodeState.VerifyingBinaries::class,
                NodeState.WritingConfig::class,
                NodeState.StartingDaemon::class,
                NodeState.ConnectingToPeers::class,
                NodeState.Syncing::class,
                NodeState.Ready::class,
            )

            if (blockedClasses.contains(currentState::class)) {
                // If JNI logs indicate the node was already running, revisit this guard.
                appendLog("Node start requested but state is $currentState; ignoring duplicate start request.")
                return@withLock false
            }

            updateState(NodeState.PreparingEnvironment, "Preparing environment…")
            true
        }

        if (!canStart) return@launch

        try {
            val paths = bootstrapper.ensureBootstrap()
            lastNodePaths = paths
            evaluateCliAvailability()

            updateState(NodeState.DownloadingBinaries(progress = 100), "Downloading binaries (100%)…")
            updateState(NodeState.VerifyingBinaries, "Verifying binaries…")
            updateState(NodeState.WritingConfig, "Writing configuration…")
            updateState(NodeState.StartingDaemon, "Starting DigiByte daemon…")

            try {
                controller.startNode(
                    context.applicationContext,
                    paths.configFile.absolutePath,
                    paths.dataDir.absolutePath
                )
            } catch (startError: Exception) {
                val message = startError.message ?: "Unknown native start error"
                appendLog("Native startNode failed: $message")
                _nodeState.value = NodeState.Error("Native startNode failed: $message")
                return@launch
            }

            val status = waitForRunningStatus()
            if (!status.equals("RUNNING", ignoreCase = true)) {
                val message = "DigiByte node failed to start (status: ${status ?: "unknown"}). Check logs for details."
                appendLog(message)
                _nodeState.value = NodeState.Error(message)
                return@launch
            }

            updateState(NodeState.ConnectingToPeers, "Connecting to peers…")
            monitorSyncState()
        } catch (e: Exception) {
            val message = e.message ?: "Unknown error starting node"
            appendLog("Error: $message")
            _nodeState.value = NodeState.Error(message)
        }
    }

    suspend fun stopNode() = withContext(Dispatchers.IO) {
        try {
            val cliAvailable = evaluateCliAvailability()
            if (cliAvailable) {
                stopWithCli()
            } else {
                appendLog("Stopping DigiByte daemon without digibyte-cli (using native controller).")
                stopWithController()
            }
        } catch (e: Exception) {
            val message = e.message ?: "Unknown error stopping node"
            appendLog("Error: $message")
            _nodeState.value = NodeState.Error("Failed to stop node")
        }
    }

    private suspend fun stopWithCli() {
        val paths = lastNodePaths ?: runCatching { bootstrapper.ensureBootstrap() }.getOrNull()
        if (paths == null) {
            appendLog("Missing node paths for CLI stop; attempting native shutdown instead.")
            stopWithController()
            return
        }

        val cliBinary = File(context.filesDir, "bin/digibyte-cli")
        if (!cliBinary.exists()) {
            appendLog("digibyte-cli binary missing on disk; attempting native shutdown instead.")
            stopWithController()
            return
        }

        appendLog("Stopping DigiByte daemon…")
        val stopProcess = ProcessBuilder(
            cliBinary.absolutePath,
            "-conf=${paths.configFile.absolutePath}",
            "-datadir=${paths.dataDir.absolutePath}",
            "stop"
        )
            .redirectErrorStream(true)
            .start()

        val output = stopProcess.inputStream.bufferedReader().use { it.readText() }.trim()
        val exitCode = stopProcess.waitFor()
        if (exitCode != 0) {
            appendLog("digibyte-cli stop failed with exit $exitCode: $output")
            stopWithController()
            return
        }

        if (output.isNotEmpty()) {
            appendLog(output)
        }

        waitForShutdown()
    }

    private suspend fun stopWithController() {
        runCatching { controller.stopNode() }
            .onFailure { error ->
                val message = error.message ?: "Failed to stop node via controller"
                appendLog("Error: $message")
                _nodeState.value = NodeState.Error("Failed to stop node")
                return
            }

        waitForShutdown()
    }

    private suspend fun waitForShutdown() {
        val deadline = System.currentTimeMillis() + STOP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val status = runCatching { controller.getStatus() }.getOrNull()
            val isRunning = status?.equals("RUNNING", ignoreCase = true) == true
            if (!isRunning) {
                updateState(NodeState.Idle, "Node stopped")
                return
            }
            delay(STOP_POLL_DELAY_MS)
        }

        val message = "Node did not stop within ${STOP_TIMEOUT_MS / 1000} seconds"
        appendLog("Error: $message")
        _nodeState.value = NodeState.Error("Failed to stop node")
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
                        val message = "DigiByte node stopped unexpectedly. It may not be supported on this device/emulator."
                        updateState(NodeState.Error(message), message)
                        return
                    }
                    status.equals("NOT_RUNNING", ignoreCase = true) -> {
                        val message = "DigiByte node stopped unexpectedly. It may not be supported on this device/emulator."
                        updateState(NodeState.Error(message), message)
                        return
                    }
                }
            }

            val cliAvailable = evaluateCliAvailability()
            val blockchainInfo = if (cliAvailable) queryBlockchainInfo(paths) else null
            val targetHeight = blockchainInfo?.targetHeight
            val currentHeight = blockchainInfo?.blocks
            val progress = blockchainInfo?.progressPercent()

            val isSynced = blockchainInfo?.let { info ->
                info.progressPercent() >= READY_THRESHOLD_PERCENT ||
                    info.headers - info.blocks <= SYNC_TOLERANCE
            } == true

            val nextState = if (isSynced) {
                NodeState.Ready
            } else {
                NodeState.Syncing(progress?.roundToInt(), currentHeight, targetHeight)
            }

            val syncLogMessage = when {
                isSynced -> "Node is fully synced and ready"
                !cliAvailable && blockchainInfo == null -> "Syncing: height unknown (digibyte-cli not available in this build)"
                else -> formatSyncLog(currentHeight, targetHeight, progress)
            }

            val currentState = _nodeState.value
            if (currentState != nextState) {
                updateState(nextState, syncLogMessage)
            } else if (nextState is NodeState.Syncing && currentState is NodeState.Syncing) {
                if (currentState.progress != nextState.progress ||
                    currentState.currentHeight != nextState.currentHeight ||
                    currentState.targetHeight != nextState.targetHeight
                ) {
                    updateState(nextState, syncLogMessage)
                }
            }

            delay(5_000)
        }
    }

    private fun queryBlockchainInfo(paths: NodeBootstrapper.NodePaths): BlockchainInfo? {
        return runCatching {
            val cliBinary = File(context.filesDir, "bin/digibyte-cli")
            if (!cliBinary.exists()) return null
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
        private const val STOP_TIMEOUT_MS = 30_000L
        private const val STOP_POLL_DELAY_MS = 1_000L
        private const val START_STATUS_RETRY_DELAY_MS = 1_000L
        private const val START_STATUS_MAX_ATTEMPTS = 30
    }

    private fun evaluateCliAvailability(): Boolean {
        if (cliAvailability == CliAvailability.Available) return true
        if (cliAvailability == CliAvailability.Missing) return false

        val cliBinary = bootstrapper.ensureCliBinary()
        val isPresent = cliBinary?.exists() == true
        cliAvailability = if (isPresent) CliAvailability.Available else CliAvailability.Missing

        if (!isPresent && !cliWarningLogged) {
            appendLog("digibyte-cli asset is not available in this build; CLI features will be disabled.")
            cliWarningLogged = true
        }

        return isPresent
    }

    private fun formatSyncLog(currentHeight: Long?, targetHeight: Long?, progress: Double?): String {
        val progressText = progress?.roundToInt()?.let { "$it%" } ?: "progress unknown"
        val heightText = if (currentHeight != null && targetHeight != null) {
            "$currentHeight / $targetHeight"
        } else {
            "height unknown"
        }
        return "Syncing: height $heightText ($progressText)"
    }

    private enum class CliAvailability {
        Unknown,
        Available,
        Missing
    }

    fun appendLog(message: String) {
        _logLines.tryEmit(message)
    }

    private fun updateState(state: NodeState, logMessage: String) {
        _nodeState.value = state
        appendLog(logMessage)
    }

    private suspend fun waitForRunningStatus(): String? {
        var lastStatus: String? = null
        repeat(START_STATUS_MAX_ATTEMPTS) {
            lastStatus = runCatching { controller.getStatus() }.getOrElse { error ->
                appendLog("Failed to read node status: ${error.message}")
                null
            }

            if (lastStatus?.equals("RUNNING", ignoreCase = true) == true) {
                return lastStatus
            }

            if (lastStatus?.equals("ERROR", ignoreCase = true) == true ||
                lastStatus?.equals("BINARY_MISSING", ignoreCase = true) == true
            ) {
                return lastStatus
            }

            delay(START_STATUS_RETRY_DELAY_MS)
        }
        return lastStatus
    }
}
