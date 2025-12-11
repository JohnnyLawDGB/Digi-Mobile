package com.digimobile.node

import android.content.Context
import com.digimobile.app.NodeBootstrapper
import com.digimobile.app.ChainstateBootstrapper
import com.digimobile.app.NodeConfigStore
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

class NodeManager(
    private val context: Context,
    private val bootstrapper: NodeBootstrapper,
    private val controller: DigiMobileNodeController,
    private val scope: CoroutineScope
) {

    private val configStore = NodeConfigStore(context)
    private val chainstateBootstrapper = ChainstateBootstrapper(context)

    private val _nodeState = MutableStateFlow<NodeState>(NodeState.Idle)
    val nodeState: StateFlow<NodeState> get() = _nodeState

    private var lastNodePaths: NodeBootstrapper.NodePaths? = null
    private var cliAvailability: CliAvailability = CliAvailability.Unknown
    var cliAvailable: Boolean = false
        private set

    private var lastSyncProgress: SyncProgress = SyncProgress(fraction = 0f, isInitialDownload = true)
    private var lastSyncSample: SyncSample? = null

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
            val binariesReady = bootstrapper.downloadOrUpdateBinaries { appendLog(it) }
            if (!binariesReady) {
                _nodeState.value = NodeState.Error("Failed to prepare DigiByte binaries")
                return@launch
            }

            val paths = bootstrapper.ensureBootstrap()
            lastNodePaths = paths

            if (configStore.shouldUseSnapshot()) {
                updateState(
                    NodeState.ApplyingSnapshot(SnapshotPhase.Download, null),
                    "Downloading snapshot..."
                )
                val snapshotFile = chainstateBootstrapper.ensureSnapshotDownloaded(
                    onProgress = { percent ->
                        updateState(
                            NodeState.ApplyingSnapshot(SnapshotPhase.Download, percent),
                            "Downloading snapshot (${percent}%)"
                        )
                    },
                    onLog = { msg -> appendLog(msg) }
                )

                if (snapshotFile != null) {
                    updateState(
                        NodeState.ApplyingSnapshot(SnapshotPhase.Verify, null),
                        "Verifying snapshot..."
                    )

                    updateState(
                        NodeState.ApplyingSnapshot(SnapshotPhase.Extract, 0),
                        "Extracting snapshot..."
                    )
                    val extractedOk = chainstateBootstrapper.extractSnapshotInto(paths.dataDir) { percent ->
                        updateState(
                            NodeState.ApplyingSnapshot(SnapshotPhase.Extract, percent),
                            "Extracting snapshot (${percent}%)"
                        )
                    }

                    if (!extractedOk) {
                        appendLog("Snapshot extraction failed, falling back to full sync.")
                    } else {
                        appendLog("Snapshot applied successfully at height ${ChainstateBootstrapper.SNAPSHOT_HEIGHT}.")
                    }
                } else {
                    appendLog("Snapshot download failed, falling back to full sync.")
                }
            }

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

            // If snapshot was applied this run
            if (chainstateBootstrapper.isSnapshotApplied()) {
                val ok = verifySnapshotHeader(
                    ChainstateBootstrapper.SNAPSHOT_HEIGHT,
                    ChainstateBootstrapper.SNAPSHOT_HASH
                )
                if (!ok) {
                    appendLog("Snapshot header mismatch; clearing chainstate and falling back to full sync.")
                    // Remove the bad chainstate and reset flag
                    File(paths.dataDir, "chainstate").deleteRecursively()
                    chainstateBootstrapper.resetSnapshotFlag()
                    _nodeState.value = NodeState.Error("Snapshot verification failed; restart to perform full sync")
                    return@launch
                }
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
        var warmupAttempts = 0
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
            if (!cliAvailable) {
                warmupAttempts = 0
                val message = "Node is running; waiting for sync details (digibyte-cli not available in this build)."
                updateState(NodeState.StartingUp(message), message)
                delay(WARMUP_RETRY_DELAY_MS)
                continue
            }

            val blockchainResult = queryBlockchainInfo(paths)

            val blockchainInfo = when (blockchainResult) {
                is QueryOutcome.Success -> blockchainResult.value
                is QueryOutcome.Warmup -> {
                    warmupAttempts++
                    val message = blockchainResult.message
                    updateState(NodeState.StartingUp(message), message)
                    if (warmupAttempts >= START_WARMUP_MAX_ATTEMPTS) {
                        val failureMessage =
                            "DigiByte RPC still starting after ${
                                (warmupAttempts * WARMUP_RETRY_DELAY_MS) / 1000
                            } seconds"
                        updateState(NodeState.Error(failureMessage), failureMessage)
                        return
                    }
                    delay(WARMUP_RETRY_DELAY_MS)
                    continue
                }
                is QueryOutcome.Failure -> {
                    updateState(NodeState.Error(blockchainResult.message), blockchainResult.message)
                    return
                }
            }
            warmupAttempts = 0

            val networkInfo = if (cliAvailable) queryNetworkInfo() else null
            val headerHeight = blockchainInfo?.headers
            val currentHeight = blockchainInfo?.blocks
            val syncProgress = blockchainInfo?.syncProgress(lastSyncProgress) ?: lastSyncProgress
            lastSyncProgress = syncProgress
            val peerCount = blockchainInfo?.connections ?: networkInfo?.connections

            val now = System.currentTimeMillis()
            val downloadRate = if (currentHeight != null) {
                lastSyncSample?.let { sample ->
                    val heightDelta = currentHeight - sample.height
                    val elapsedSeconds = (now - sample.timestampMs) / 1000.0
                    if (heightDelta >= 0 && elapsedSeconds > 0) {
                        heightDelta / elapsedSeconds
                    } else {
                        null
                    }
                }
            } else {
                null
            }
            lastSyncSample = currentHeight?.let { SyncSample(it, now) }

            val isSynced = blockchainInfo?.let { info ->
                syncProgress.fraction.toDouble() >= READY_THRESHOLD_FRACTION ||
                    ((info.headers ?: 0) - (info.blocks ?: 0)) <= SYNC_TOLERANCE ||
                    info.isInitialBlockDownload == false
            } == true

            val nextState = if (isSynced) {
                NodeState.Ready
            } else {
                NodeState.Syncing(currentHeight, headerHeight, syncProgress, peerCount, downloadRate)
            }

            val syncLogMessage = when {
                isSynced -> "Node is fully synced and ready"
                !cliAvailable && blockchainInfo == null -> "Node is running; waiting for sync details (digibyte-cli not available in this build)."
                else -> formatSyncLog(currentHeight, headerHeight, syncProgress, peerCount, downloadRate)
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

    private suspend fun queryBlockchainInfo(paths: NodeBootstrapper.NodePaths): QueryOutcome<BlockchainInfo> {
        val result = runCliCommand(listOf("getblockchaininfo"))
        if (result.exitCode != 0) {
            val warmupMessage = result.toWarmupMessage()
            if (warmupMessage != null) {
                return QueryOutcome.Warmup(warmupMessage)
            }
            handleCliError("digibyte-cli getblockchaininfo failed with exit ${result.exitCode}: ${result.stderr.ifEmpty { result.stdout }}")
            return QueryOutcome.Failure("digibyte-cli getblockchaininfo failed")
        }

        return runCatching {
            val json = JSONObject(result.stdout)
            val blocks = json.optLong("blocks")
            val headers = json.optLong("headers")
            val verificationProgress = json.optDouble("verificationprogress")
            val initialBlockDownload = json.optBoolean("initialblockdownload")
            val connections = json.optInt("connections", -1).takeIf { it >= 0 }
            QueryOutcome.Success(
                BlockchainInfo(blocks, headers, verificationProgress, initialBlockDownload, connections)
            )
        }.getOrElse { error ->
            handleCliError("Failed to parse getblockchaininfo: ${error.message}")
            QueryOutcome.Failure("Failed to parse getblockchaininfo")
        }
    }

    private suspend fun queryNetworkInfo(): NetworkInfo? {
        val result = runCliCommand(listOf("getnetworkinfo"))
        if (result.exitCode != 0) {
            val warmupMessage = result.toWarmupMessage()
            if (warmupMessage == null) {
                handleCliError("digibyte-cli getnetworkinfo failed with exit ${result.exitCode}: ${result.stderr.ifEmpty { result.stdout }}")
            }
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

    suspend fun executeCliCommand(args: List<String>): CliResult = runCliCommand(args)

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

    private fun CliResult.toWarmupMessage(): String? {
        val text = (stderr.ifEmpty { stdout }).lowercase()
        if (exitCode == CLI_UNAVAILABLE_EXIT) return "Node starting… waiting for RPC access"

        val warmupSignals = listOf(
            "rpc in warmup",
            "rpc in warm up",
            "loading block index",
            "verifying blocks",
            "still warming up",
            "loading wallet",
            "loading wallets",
            "rescanning",
            "verifying wallet",
        )
        if (warmupSignals.any { text.contains(it) }) {
            return "Node starting… this can take a few minutes"
        }

        val connectionSignals = listOf(
            "couldn't connect to server",
            "connection refused",
            "connection reset",
            "connect error",
            "could not connect",
        )
        return connectionSignals.firstOrNull { text.contains(it) }?.let {
            "Node starting… waiting for RPC access"
        }
    }

    private fun handleCliError(message: String) {
        if (!cliSyncErrorLogged) {
            appendLog(message)
            cliSyncErrorLogged = true
        }
    }

    private suspend fun verifySnapshotHeader(height: Long, expectedHash: String): Boolean {
        val result = runCliCommand(listOf("getblockhash", height.toString()))
        if (result.exitCode != 0) {
            appendLog("Snapshot verification failed: ${result.stderr.ifEmpty { result.stdout }}")
            return false
        }
        return result.stdout.trim().equals(expectedHash, ignoreCase = true)
    }

    private fun formatSyncLog(
        currentHeight: Long?,
        headerHeight: Long?,
        progress: SyncProgress?,
        peerCount: Int?,
        downloadRate: Double?
    ): String {
        val progressText = progress?.let { "${it.toProgressInt()}%" } ?: "progress unknown"
        val heightText = if (currentHeight != null && headerHeight != null) {
            "$currentHeight / $headerHeight"
        } else {
            "height unknown"
        }
        val peersText = peerCount?.let { " with $it peers" } ?: ""
        val rateText = downloadRate?.let { " at ${String.format("%.2f", it)} blk/s" } ?: ""
        return "Syncing: height $heightText (~$progressText$peersText$rateText)"
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
        val isInitialBlockDownload: Boolean?,
        val connections: Int?,
    ) {
        fun syncProgress(previous: SyncProgress?): SyncProgress {
            val previousProgress = previous ?: SyncProgress(0f, isInitialDownload = true)

            if (isInitialBlockDownload == false) {
                return SyncProgress(1f, isInitialDownload = false)
            }
            verificationProgress?.let { return SyncProgress(it.coerceIn(0.0, 1.0).toFloat(), isInitialDownload = true) }

            return previousProgress
        }
    }

    private data class NetworkInfo(
        val connections: Int?,
    )

    private data class SyncSample(
        val height: Long,
        val timestampMs: Long,
    )

    data class CliResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private sealed class QueryOutcome<out T> {
        data class Success<T>(val value: T) : QueryOutcome<T>()
        data class Warmup(val message: String) : QueryOutcome<Nothing>()
        data class Failure(val message: String) : QueryOutcome<Nothing>()
    }

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
        private const val WARMUP_RETRY_DELAY_MS = 5_000L
        private const val START_WARMUP_MAX_ATTEMPTS = 12
        private const val CLI_UNAVAILABLE_EXIT = -1
    }

    fun getStatusSnapshot(): NodeStatusSnapshot {
        val paths = lastNodePaths ?: runCatching { bootstrapper.ensureBootstrap() }.getOrNull()
        val dataDir = paths?.dataDir ?: File(context.filesDir, "digibyte")
        val configFile = paths?.configFile ?: File(dataDir, "digibyte.conf")
        val debugLogInsight = NodeDiagnostics.debugLogConfig(dataDir)

        return NodeStatusSnapshot(
            datadir = dataDir,
            confExists = configFile.exists(),
            debugLogInsight = debugLogInsight
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
    val confExists: Boolean,
    val debugLogInsight: DebugLogInsight,
)
