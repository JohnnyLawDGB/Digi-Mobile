package com.digimobile.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.digimobile.app.databinding.ActivityMainBinding
import com.digimobile.node.NodeManager
import com.digimobile.node.NodeManagerProvider
import com.digimobile.node.NodeState
import com.digimobile.node.toUserMessage
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bootstrapper: NodeBootstrapper
    private lateinit var nodeManager: NodeManager
    private var lastNodeState: NodeState = NodeState.Idle

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
                "First launch detected. Use the setup wizard to choose pruning/storage, peers, and bandwidth preferences."
        }

        binding.buttonStartNode.setOnClickListener {
            openSetupWizard()
        }
        binding.buttonDetails.setOnClickListener { openNodeSetupActivity() }
        binding.textStatus.setOnClickListener {
            if (lastNodeState is NodeState.Ready) {
                openCoreConsoleActivity()
            } else {
                openNodeSetupActivity()
            }
        }
        binding.buttonOpenConsole.setOnClickListener { openCoreConsoleActivity() }

        observeNodeState()
    }

    private fun observeNodeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                nodeManager.nodeState.collect { state ->
                    lastNodeState = state
                    updateStatusLabel(state.toUserMessage(this@MainActivity))
                    updateButtonForState(state)
                    updateConsoleButton(state)
                }
            }
        }
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
            is NodeState.StartingUp,
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
                binding.buttonStartNode.text = "Set up and start node"
                Toast.makeText(
                    this@MainActivity,
                    "Something went wrong. Tap to try starting again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateStatusLabel(status: String) {
        binding.textStatus.text = "Status: $status"
    }

    private fun updateConsoleButton(state: NodeState) {
        binding.buttonOpenConsole.isEnabled = state is NodeState.Ready
        binding.buttonOpenConsole.isClickable = state is NodeState.Ready
        binding.buttonOpenConsole.isVisible = state is NodeState.Ready
    }

    private fun openNodeSetupActivity() {
        val intent = Intent(this, NodeSetupActivity::class.java)
        startActivity(intent)
    }

    private fun openCoreConsoleActivity() {
        val intent = Intent(this, CoreConsoleActivity::class.java)
        startActivity(intent)
    }

    private fun openSetupWizard() {
        val intent = Intent(this, SetupWizardActivity::class.java)
        startActivity(intent)
    }
}
