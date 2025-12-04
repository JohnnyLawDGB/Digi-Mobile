package com.digimobile.app

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.digimobile.app.databinding.ActivityNodeSetupBinding
import com.digimobile.node.NodeManager
import com.digimobile.node.NodeManagerProvider
import com.digimobile.node.NodeState
import com.digimobile.node.toUserMessage
import java.util.ArrayDeque
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NodeSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNodeSetupBinding
    private lateinit var nodeManager: NodeManager
    private var lastNodeState: NodeState = NodeState.Idle
    private val logBuffer = ArrayDeque<String>()

    companion object {
        private const val MAX_LOG_LINES = 500
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNodeSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nodeManager = NodeManagerProvider.get(applicationContext)

        setupLogsPlaceholder()
        observeNodeState()
        observeLogs()
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
        binding.textMainStatus.text = state.toUserMessage()
        if (state is NodeState.Ready && previousState !is NodeState.Ready) {
            nodeManager.appendLog("Node is ready to accept CLI commands.")
            Toast.makeText(this, "Node is fully synced and ready", Toast.LENGTH_SHORT).show()
        }
        updateProgress(state)
        updateSteps(state, previousState)
        updateActionButton(state)
    }

    private fun appendLogLine(line: String) {
        logBuffer.addLast(line)
        while (logBuffer.size > MAX_LOG_LINES) {
            logBuffer.removeFirst()
        }
        binding.textLogs.text = logBuffer.joinToString(separator = "\n")
    }

    private fun updateProgress(state: NodeState) {
        when (state) {
            is NodeState.DownloadingBinaries -> showProgress(state.progress)
            is NodeState.Syncing -> showProgress(state.progress)
            else -> hideProgress()
        }
    }

    private fun showProgress(progress: Int) {
        binding.progressBar.isVisible = true
        binding.progressBar.progress = progress
    }

    private fun hideProgress() {
        binding.progressBar.isVisible = false
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
            NodeState.ConnectingToPeers,
            is NodeState.Syncing -> {
                binding.buttonAction.text = "Stop node"
                binding.buttonAction.setOnClickListener {
                    nodeManager.stopNode()
                    finish()
                }
            }
            NodeState.Ready -> {
                binding.buttonAction.text = "Open core console"
                binding.buttonAction.isEnabled = true
                binding.buttonAction.setOnClickListener {
                    Toast.makeText(this, "Core console coming soon", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                binding.buttonAction.text = "Back"
                binding.buttonAction.setOnClickListener { finish() }
            }
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
