package com.digimobile.app

import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.digimobile.app.databinding.ActivityNodeSetupBinding
import com.digimobile.node.DebugLogStatus
import com.digimobile.node.NodeDiagnostics
import com.digimobile.node.NodeManager
import com.digimobile.node.NodeManagerProvider
import com.digimobile.node.NodeStatusSnapshot
import com.digimobile.node.NodeState
import com.digimobile.node.SyncProgress
import com.digimobile.node.toUserMessage
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NodeSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNodeSetupBinding
    private lateinit var nodeManager: NodeManager
    private var lastNodeState: NodeState = NodeState.Idle
    private val logBuffer = ArrayDeque<String>()
    private var diagnosticsLogged = false

    companion object {
        private const val MAX_LOG_LINES = 500
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNodeSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nodeManager = NodeManagerProvider.get(applicationContext)

        setupLogsPlaceholder()
        setupDeveloperInfo()
        updateHelperText(NodeState.Idle)
        observeNodeState()
        observeLogs()
        refreshDeveloperInfo()
    }

    private fun setupLogsPlaceholder() {
        binding.textLogs.text = "Detailed logs will appear here."
        binding.textLogs.movementMethod = ScrollingMovementMethod()
    }

    private fun observeLogs() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                nodeManager.logLines.collect { line ->
                    withContext(Dispatchers.Main) { appendLogLine(line) }
                }
            }
        }
    }

    private fun observeNodeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                nodeManager.nodeState.collect { state ->
                    updateStatus(state, lastNodeState)
                    lastNodeState = state
                }
            }
        }
    }

    private fun updateStatus(state: NodeState, previousState: NodeState) {
        if (state is NodeState.Idle) {
            diagnosticsLogged = false
        }

        binding.textMainStatus.text = if (state is NodeState.Error) {
            "Error: ${state.message}"
        } else {
            state.toUserMessage(this)
        }
        updateHelperText(state)
        updateErrorBanner(state)
        if (state is NodeState.Error) {
            appendLogLine("Error detail: ${state.message}")
        }
        if (state is NodeState.Ready && previousState !is NodeState.Ready) {
            if (nodeManager.cliAvailable) {
                nodeManager.appendLog("Node is ready to accept CLI commands.")
            } else {
                nodeManager.appendLog("Node is synced, but digibyte-cli is not available in this build.")
            }
            Toast.makeText(this, "Node is fully synced and ready", Toast.LENGTH_SHORT).show()
        }
        updateProgress(state)
        updateSteps(state, previousState)
        updateActionButton(state)
        maybeLogDiagnostics(state, previousState)
    }

    private fun maybeLogDiagnostics(state: NodeState, previousState: NodeState) {
        if (diagnosticsLogged) return
        if (!isRunningState(state) || isRunningState(previousState)) return

        diagnosticsLogged = true
        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) { nodeManager.getStatusSnapshot() }
            val debugLogFile = snapshot.debugLogInsight.file
            val tail = withContext(Dispatchers.IO) {
                NodeDiagnostics.tailDebugLog(snapshot.datadir, maxLines = 50)
            }

            withContext(Dispatchers.Main) { updateDeveloperInfo(snapshot) }

            val message: String
            val linesToAppend: List<String>

            when (snapshot.debugLogInsight.status) {
                DebugLogStatus.Disabled -> {
                    message = "Debug log disabled by configuration (pruned profile)."
                    linesToAppend = emptyList()
                }
                DebugLogStatus.Present -> {
                    val logPath = debugLogFile?.absolutePath ?: "debug.log"
                    message = "Debug log found at $logPath; use tail view below for recent lines"
                    linesToAppend = tail
                }
                DebugLogStatus.Missing -> {
                    message = "No debug.log yet; node may still be initializing."
                    linesToAppend = emptyList()
                }
            }

            withContext(Dispatchers.Main) {
                appendLogLine(message)
                linesToAppend.forEach { appendLogLine("[debug.log] $it") }
            }
        }
    }

    private fun isRunningState(state: NodeState): Boolean {
        return state is NodeState.ConnectingToPeers ||
            state is NodeState.StartingUp ||
            state is NodeState.Syncing ||
            state is NodeState.Ready
    }

    private fun appendLogLine(line: String) {
        logBuffer.addLast(line)
        while (logBuffer.size > MAX_LOG_LINES) {
            logBuffer.removeFirst()
        }
        binding.textLogs.text = logBuffer.joinToString(separator = "\n")
    }

    private fun setupDeveloperInfo() {
        binding.layoutDeveloperInfoHeader.setOnClickListener {
            binding.layoutDeveloperInfoBody.isVisible = !binding.layoutDeveloperInfoBody.isVisible
            binding.textDeveloperInfoToggle.text = if (binding.layoutDeveloperInfoBody.isVisible) {
                "Hide"
            } else {
                "Show"
            }
        }
    }

    private fun refreshDeveloperInfo() {
        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) { nodeManager.getStatusSnapshot() }
            withContext(Dispatchers.Main) { updateDeveloperInfo(snapshot) }
        }
    }

    private fun updateDeveloperInfo(snapshot: NodeStatusSnapshot) {
        binding.textDeveloperDatadir.text = "Datadir: ${snapshot.datadir.absolutePath}"
        binding.textDeveloperConf.text =
            "digibyte.conf: ${if (snapshot.confExists) "exists" else "absent"}"
        val debugLogLabel = when (snapshot.debugLogInsight.status) {
            DebugLogStatus.Disabled -> "disabled (pruned config)"
            DebugLogStatus.Present -> "exists"
            DebugLogStatus.Missing -> "absent"
        }
        binding.textDeveloperDebugLog.text = "debug.log: $debugLogLabel"
    }

    private fun updateProgress(state: NodeState) {
        when (state) {
            is NodeState.DownloadingBinaries -> showDownloadProgress(state.progress / 100.0)
            is NodeState.ApplyingSnapshot -> {
                if (state.progress != null) showDownloadProgress(state.progress / 100.0)
                else showIndeterminateProgress()
            }
            is NodeState.StartingUp -> showIndeterminateProgress()
            is NodeState.Syncing -> showSyncProgress(state.progress)
            else -> hideProgress()
        }
    }

    private fun showDownloadProgress(progress: Double?) {
        binding.progressBar.isVisible = true
        binding.progressBar.isIndeterminate = progress == null
        if (progress != null) {
            binding.progressBar.progress = (progress * binding.progressBar.max).toInt().coerceIn(0, binding.progressBar.max)
        }
    }

    private fun showSyncProgress(progress: SyncProgress) {
        binding.progressBar.isVisible = true
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = progress.toProgressInt(binding.progressBar.max)
    }

    private fun showIndeterminateProgress() {
        binding.progressBar.isVisible = true
        binding.progressBar.isIndeterminate = true
    }

    private fun hideProgress() {
        binding.progressBar.isVisible = false
        binding.progressBar.isIndeterminate = false
    }

    private fun updateSteps(state: NodeState, previousState: NodeState) {
        val statuses = determineStepStatuses(state, previousState)
        binding.textStepPrepareStatus.text = statuses[SetupStep.PrepareEnvironment]?.label
        binding.textStepDownloadStatus.text = statuses[SetupStep.DownloadBinaries]?.label
        binding.textStepVerifyStatus.text = statuses[SetupStep.VerifyBinaries]?.label
        binding.textStepConfigStatus.text = statuses[SetupStep.WriteConfig]?.label
        binding.textStepStartStatus.text = statuses[SetupStep.StartNode]?.label
        binding.textStepPeersStatus.text = statuses[SetupStep.ConnectPeers]?.label
        binding.textStepSyncStatus.text = statuses[SetupStep.SyncBlockchain]?.label
    }

    private fun determineStepStatuses(state: NodeState, previousState: NodeState): Map<SetupStep, StepStatus> {
        if (state is NodeState.Error) {
            val baseline = if (previousState is NodeState.Error) {
                baseStatusesForState(NodeState.Idle)
            } else {
                baseStatusesForState(previousState)
            }
            val firstIncomplete = SetupStep.values().firstOrNull { baseline[it] != StepStatus.Done }
                ?: SetupStep.SyncBlockchain
            baseline[firstIncomplete] = StepStatus.Error
            return baseline
        }

        return baseStatusesForState(state)
    }

    private fun baseStatusesForState(state: NodeState): MutableMap<SetupStep, StepStatus> {
        val statuses = SetupStep.values().associateWith { StepStatus.Pending }.toMutableMap()

        when (state) {
            NodeState.Idle -> Unit
            NodeState.PreparingEnvironment -> statuses[SetupStep.PrepareEnvironment] = StepStatus.InProgress
            is NodeState.DownloadingBinaries -> {
                statuses[SetupStep.PrepareEnvironment] = StepStatus.Done
                statuses[SetupStep.DownloadBinaries] = StepStatus.InProgress
            }
            NodeState.VerifyingBinaries -> {
                statuses[SetupStep.PrepareEnvironment] = StepStatus.Done
                statuses[SetupStep.DownloadBinaries] = StepStatus.Done
                statuses[SetupStep.VerifyBinaries] = StepStatus.InProgress
            }
            NodeState.WritingConfig -> {
                statuses[SetupStep.PrepareEnvironment] = StepStatus.Done
                statuses[SetupStep.DownloadBinaries] = StepStatus.Done
                statuses[SetupStep.VerifyBinaries] = StepStatus.Done
                statuses[SetupStep.WriteConfig] = StepStatus.InProgress
            }
            NodeState.StartingDaemon -> {
                statuses[SetupStep.PrepareEnvironment] = StepStatus.Done
                statuses[SetupStep.DownloadBinaries] = StepStatus.Done
                statuses[SetupStep.VerifyBinaries] = StepStatus.Done
                statuses[SetupStep.WriteConfig] = StepStatus.Done
                statuses[SetupStep.StartNode] = StepStatus.InProgress
            }
            is NodeState.ApplyingSnapshot -> {
                statuses[SetupStep.PrepareEnvironment] = StepStatus.Done
                statuses[SetupStep.DownloadBinaries] = StepStatus.Done
                statuses[SetupStep.VerifyBinaries] = StepStatus.Done
                statuses[SetupStep.WriteConfig] = StepStatus.Done
                statuses[SetupStep.StartNode] = StepStatus.InProgress
            }
            is NodeState.StartingUp -> {
                statuses[SetupStep.PrepareEnvironment] = StepStatus.Done
                statuses[SetupStep.DownloadBinaries] = StepStatus.Done
                statuses[SetupStep.VerifyBinaries] = StepStatus.Done
                statuses[SetupStep.WriteConfig] = StepStatus.Done
                statuses[SetupStep.StartNode] = StepStatus.InProgress
            }
            NodeState.ConnectingToPeers -> {
                statuses[SetupStep.PrepareEnvironment] = StepStatus.Done
                statuses[SetupStep.DownloadBinaries] = StepStatus.Done
                statuses[SetupStep.VerifyBinaries] = StepStatus.Done
                statuses[SetupStep.WriteConfig] = StepStatus.Done
                statuses[SetupStep.StartNode] = StepStatus.Done
                statuses[SetupStep.ConnectPeers] = StepStatus.InProgress
            }
            is NodeState.Syncing -> {
                statuses[SetupStep.PrepareEnvironment] = StepStatus.Done
                statuses[SetupStep.DownloadBinaries] = StepStatus.Done
                statuses[SetupStep.VerifyBinaries] = StepStatus.Done
                statuses[SetupStep.WriteConfig] = StepStatus.Done
                statuses[SetupStep.StartNode] = StepStatus.Done
                statuses[SetupStep.ConnectPeers] = StepStatus.Done
                statuses[SetupStep.SyncBlockchain] = StepStatus.InProgress
            }
            NodeState.Ready -> SetupStep.values().forEach { statuses[it] = StepStatus.Done }
            is NodeState.Error -> Unit
        }

        return statuses
    }

    private fun updateActionButton(state: NodeState) {
        when (state) {
            NodeState.PreparingEnvironment,
            is NodeState.DownloadingBinaries,
            NodeState.VerifyingBinaries,
            NodeState.WritingConfig,
            NodeState.StartingDaemon,
            is NodeState.ApplyingSnapshot,
            is NodeState.StartingUp,
            NodeState.ConnectingToPeers,
            is NodeState.Syncing,
            NodeState.Ready -> {
                binding.buttonAction.text = "Open core console"
                binding.buttonAction.isEnabled = true
                binding.buttonAction.setOnClickListener {
                    val intent = Intent(this, CoreConsoleActivity::class.java)
                    startActivity(intent)
                }
            }
            is NodeState.Error -> {
                binding.buttonAction.text = "Back to home"
                binding.buttonAction.isEnabled = true
                binding.buttonAction.setOnClickListener { finish() }
            }
            else -> {
                binding.buttonAction.text = "Back"
                binding.buttonAction.setOnClickListener { finish() }
            }
        }
    }

    private fun updateHelperText(state: NodeState) {
        val helper = when (state) {
            NodeState.Ready -> "Your phone is now running a DigiByte node. Use the core console to issue advanced commands."
            is NodeState.Syncing -> {
                val hasDetails =
                    state.currentHeight != null && state.headerHeight != null
                val peersText = state.peerCount?.let { " with $it peers" } ?: ""
                val rateText = state.downloadRate?.let { " at ${String.format("%.2f", it)} blk/s" } ?: ""
                val percent = state.progress.toProgressInt()
                val statusPrefix = if (state.progress.fraction >= 0.999f) {
                    "Synced"
                } else {
                    "Syncing blockchain…"
                }

                if (hasDetails) {
                    "$statusPrefix height ${state.currentHeight} / ${state.headerHeight} (~${percent}%${peersText}${rateText})"
                } else {
                    "$statusPrefix waiting for peer heights…"
                }
            }
            is NodeState.ApplyingSnapshot -> state.toUserMessage(this)
            is NodeState.StartingUp -> state.reason
            NodeState.ConnectingToPeers -> "Node is running; waiting for peer connections and sync details…"
            is NodeState.Error -> "Node failed to start. Return to the home screen and try again."
            else -> "We’ll download the DigiByte node binaries and sync the blockchain on this device."
        }
        binding.textHelper.text = helper
    }

    private fun updateErrorBanner(state: NodeState) {
        if (state is NodeState.Error) {
            binding.textErrorBanner.isVisible = true
            binding.textErrorBanner.text = state.message
        } else {
            binding.textErrorBanner.isVisible = false
        }
    }

    private fun confirmStopNode() {
        AlertDialog.Builder(this)
            .setTitle("Stop DigiByte node?")
            .setMessage("This will shut down the DigiByte daemon. You can restart it from the main screen.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Stop") { _, _ ->
                sendStopIntent()
                lifecycleScope.launch { nodeManager.stopNode() }
            }
            .show()
    }

    private fun sendStopIntent() {
        val stopIntent = Intent(this, NodeService::class.java).apply { action = NodeService.ACTION_STOP }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(stopIntent)
        } else {
            startService(stopIntent)
        }
    }
}

private enum class SetupStep {
    PrepareEnvironment,
    DownloadBinaries,
    VerifyBinaries,
    WriteConfig,
    StartNode,
    ConnectPeers,
    SyncBlockchain
}

private enum class StepStatus(val label: String) {
    Pending("○ Pending"),
    InProgress("⏳ In progress"),
    Done("✔ Done"),
    Error("⚠ Error")
}
