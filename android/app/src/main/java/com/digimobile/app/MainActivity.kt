package com.digimobile.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.digimobile.app.databinding.ActivityMainBinding
import com.digimobile.node.NodeManager
import com.digimobile.node.NodeManagerProvider
import com.digimobile.node.NodeState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bootstrapper: NodeBootstrapper
    private lateinit var nodeManager: NodeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bootstrapper = NodeBootstrapper(this)
        nodeManager = NodeManagerProvider.get(applicationContext)

        binding.textIntro.text = "Welcome to Digi-Mobile"
        binding.textExplanation.text =
            "Digi-Mobile wraps a pruned DigiByte node so hobbyists can contribute to the network on Android."
        binding.textDisclaimer.text =
            "⚠️ Experimental/testing only. Do not store large amounts of DigiByte here."

        if (bootstrapper.isFirstRun()) {
            binding.textSteps.text =
                "First launch detected. Tap the button to create private config/data/log folders and start the node."
        }

        binding.buttonStartNode.setOnClickListener { startNodeFlow() }

        observeNodeState()
    }

    private fun startNodeFlow() {
        val intent = Intent(this@MainActivity, NodeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            startService(intent)
        }

        nodeManager.startNode()
    }

    private fun observeNodeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                nodeManager.nodeState.collect { state ->
                    updateStatusLabel(state.toUserMessage())
                    updateButtonForState(state)
                }
            }
        }
    }

    private fun NodeState.toUserMessage(): String = when (this) {
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

    private fun updateButtonForState(state: NodeState) {
        when (state) {
            NodeState.Idle -> {
                binding.buttonStartNode.isEnabled = true
                binding.buttonStartNode.text = "Set up and start node"
            }
            NodeState.PreparingEnvironment,
            is NodeState.DownloadingBinaries,
            NodeState.VerifyingBinaries,
            NodeState.WritingConfig,
            NodeState.StartingDaemon,
            NodeState.ConnectingToPeers,
            is NodeState.Syncing -> {
                binding.buttonStartNode.isEnabled = false
                binding.buttonStartNode.text = "Starting..."
            }
            NodeState.Ready -> {
                binding.buttonStartNode.isEnabled = false
                binding.buttonStartNode.text = "Node running"
                Toast.makeText(
                    this@MainActivity,
                    "Node service started and running",
                    Toast.LENGTH_LONG
                ).show()
            }
            is NodeState.Error -> {
                binding.buttonStartNode.isEnabled = true
                binding.buttonStartNode.text = "Retry start"
                Toast.makeText(
                    this@MainActivity,
                    "Node error: ${state.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateStatusLabel(status: String) {
        binding.textStatus.text = "Status: $status"
    }
}
