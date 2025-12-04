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
    var cliAvailable: Boolean = false
        private set

    private var cliWarningLogged: Boolean = false
    private var cliSyncErrorLogged: Boolean = false

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
                // -conf and -datadir are forwarded to digibyted via nativeStartNode.
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
        appendLog("Stopping DigiByte daemon…")
        val result = runCliCommand(listOf("stop"))
        if (result.exitCode != 0) {
            appendLog("digibyte-cli stop failed with exit ${result.exitCode}: ${result.stderr.ifEmpty { result.stdout }}")
            stopWithController()
            return
        }

        if (result.stdout.isNotEmpty()) {
            appendLog(result.stdout)
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

        cliSyncErrorLogged = false
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

            cliAvailable = evaluateCliAvailability()
            val blockchainInfo = if (cliAvailable) queryBlockchainInfo(paths) else null
            val networkInfo = if (cliAvailable) queryNetworkInfo() else null
            val headerHeight = blockchainInfo?.headers
            val currentHeight = blockchainInfo?.blocks
            val progress = blockchainInfo?.progressFraction()
            val peerCount = blockchainInfo?.connections ?: networkInfo?.connections

            val isSynced = blockchainInfo?.let { info ->
                (info.progressFraction() ?: 0.0) >= READY_THRESHOLD_FRACTION ||
                    ((info.headers ?: 0) - (info.blocks ?: 0)) <= SYNC_TOLERANCE
            } == true

            val nextState = if (isSynced) {
                NodeState.Ready
            } else {
                NodeState.Syncing(currentHeight, headerHeight, progress, peerCount)
            }

            val syncLogMessage = when {
                isSynced -> "Node is fully synced and ready"
                !cliAvailable && blockchainInfo == null -> "Node is running; waiting for sync details (digibyte-cli not available in this build)."
                else -> formatSyncLog(currentHeight, headerHeight, progress, peerCount)
            }

            val currentState = _nodeState.value
            if (currentState != nextState) {
                updateState(nextState, syncLogMessage)
            } else if (nextState is NodeState.Syncing && currentState is NodeState.Syncing) {
                if (currentState.progress != nextState.progress ||
                    currentState.currentHeight != nextState.currentHeight ||
                    currentState.headerHeight != nextState.headerHeight
                ) {
                    updateState(nextState, syncLogMessage)
                }
            }

            delay(5_000)
        }
    }

    private suspend fun queryBlockchainInfo(paths: NodeBootstrapper.NodePaths): BlockchainInfo? {
        val result = runCliCommand(listOf("getblockchaininfo"))
        if (result.exitCode != 0) {
            handleCliError("digibyte-cli getblockchaininfo failed with exit ${result.exitCode}: ${result.stderr.ifEmpty { result.stdout }}")
            return null
        }

        return runCatching {
            val json = JSONObject(result.stdout)
            val blocks = json.optLong("blocks")
            val headers = json.optLong("headers")
            val verificationProgress = json.optDouble("verificationprogress")
            val connections = json.optInt("connections", -1).takeIf { it >= 0 }
            BlockchainInfo(blocks, headers, verificationProgress, connections)
        }.getOrElse { error ->
            handleCliError("Failed to parse getblockchaininfo: ${error.message}")
            null
        }
    }

    private suspend fun queryNetworkInfo(): NetworkInfo? {
        val result = runCliCommand(listOf("getnetworkinfo"))
        if (result.exitCode != 0) {
            handleCliError("digibyte-cli getnetworkinfo failed with exit ${result.exitCode}: ${result.stderr.ifEmpty { result.stdout }}")
            return null
        }

        return runCatching {
            val json = JSONObject(result.stdout)
            val connections = json.optInt("connections", -1).takeIf { it >= 0 }
            NetworkInfo(connections)
        }.getOrElse { error ->
            handleCliError("Failed to parse getnetworkinfo: ${error.message}")
            null
        }
    }

    private suspend fun runCliCommand(args: List<String>): CliResult = withContext(Dispatchers.IO) {
        val paths = NodeEnvironment.paths ?: lastNodePaths
        val credentials = NodeEnvironment.rpcCredentials
        val cliBinary = bootstrapper.ensureCliBinary() ?: File(context.filesDir, "bin/digibyte-cli")

        if (!cliAvailable || paths == null || credentials == null || !cliBinary.canExecute()) {
            return@withContext CliResult(CLI_UNAVAILABLE_EXIT, "", "digibyte-cli not available")
        }

        val command = mutableListOf(
            cliBinary.absolutePath,
            "-datadir=${paths.dataDir.absolutePath}",
            "-rpcuser=${credentials.user}",
            "-rpcpassword=${credentials.password}"
        )
        command.addAll(args)

        return@withContext runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().use { it.readText().trim() }
            val stderr = process.errorStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()
            CliResult(exitCode, stdout, stderr)
        }.getOrElse { error ->
            CliResult(CLI_UNAVAILABLE_EXIT, "", error.message ?: "Unknown CLI error")
        }
    }

    private fun handleCliError(message: String) {
        if (!cliSyncErrorLogged) {
            appendLog(message)
            cliSyncErrorLogged = true
        }
    }

    private fun formatSyncLog(currentHeight: Long?, headerHeight: Long?, progress: Double?, peerCount: Int?): String {
        val progressText = progress?.let { "${(it * 100).roundToInt()}%" } ?: "progress unknown"
        val heightText = if (currentHeight != null && headerHeight != null) {
            "$currentHeight / $headerHeight"
        } else {
            "height unknown"
        }
        val peersText = peerCount?.let { " with $it peers" } ?: ""
        return "Syncing: height $heightText (~$progressText$peersText)"
    }

    private fun evaluateCliAvailability(): Boolean {
        if (cliAvailability == CliAvailability.Available) {
            cliAvailable = true
            return true
        }
        if (cliAvailability == CliAvailability.Missing) {
            cliAvailable = false
            return false
        }

        val cliBinary = bootstrapper.ensureCliBinary()
        val isPresent = cliBinary?.exists() == true && cliBinary.canExecute()
        cliAvailability = if (isPresent) CliAvailability.Available else CliAvailability.Missing
        cliAvailable = isPresent

        if (!isPresent && !cliWarningLogged) {
            appendLog("digibyte-cli asset missing; CLI features will be disabled.")
            cliWarningLogged = true
        }

        return isPresent
    }

    private data class BlockchainInfo(
        val blocks: Long?,
        val headers: Long?,
        val verificationProgress: Double?,
        val connections: Int?,
    ) {
        fun progressFraction(): Double? {
            verificationProgress?.let { return it.coerceIn(0.0, 1.0) }
            if (blocks == null || headers == null || headers <= 0) return null
            return (blocks.toDouble() / headers).coerceIn(0.0, 1.0)
        }
    }

    private data class NetworkInfo(
        val connections: Int?,
    )

    private data class CliResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private enum class CliAvailability {
        Unknown,
        Available,
        Missing
    }

    companion object {
        private const val SYNC_TOLERANCE = 2L
        private const val READY_THRESHOLD_FRACTION = 0.999
        private const val STOP_TIMEOUT_MS = 30_000L
        private const val STOP_POLL_DELAY_MS = 1_000L
        private const val START_STATUS_RETRY_DELAY_MS = 1_000L
        private const val START_STATUS_MAX_ATTEMPTS = 30
        private const val CLI_UNAVAILABLE_EXIT = -1
    }

    fun getStatusSnapshot(): NodeStatusSnapshot {
        val paths = lastNodePaths ?: runCatching { bootstrapper.ensureBootstrap() }.getOrNull()
        val dataDir = paths?.dataDir ?: File(context.filesDir, "digibyte")
        val configFile = paths?.configFile ?: File(dataDir, "digibyte.conf")
        val debugLogFile = NodeDiagnostics.getDebugLogFile(dataDir)

        return NodeStatusSnapshot(
            datadir = dataDir,
            hasConfig = configFile.exists(),
            hasDebugLog = debugLogFile.exists()
        )
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

data class NodeStatusSnapshot(
    val datadir: File,
    val hasConfig: Boolean,
    val hasDebugLog: Boolean,
)
