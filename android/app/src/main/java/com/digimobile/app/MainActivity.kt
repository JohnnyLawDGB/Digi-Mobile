package com.digimobile.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.digimobile.app.databinding.ActivityMainBinding
import com.digimobile.node.DigiMobileNodeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bootstrapper: NodeBootstrapper
    private val nodeController = DigiMobileNodeController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bootstrapper = NodeBootstrapper(this)

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

        updateStatusLabel(nodeController.statusText())
    }

    private fun startNodeFlow() {
        binding.buttonStartNode.isEnabled = false
        updateStatusLabel("Preparing node...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Ensure directories/config are ready even before the service runs.
                val paths = bootstrapper.ensureBootstrap()
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@MainActivity, NodeService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        @Suppress("DEPRECATION")
                        startService(intent)
                    }
                    updateStatusLabel("Starting node service...")
                }

                observeNodeStartup(paths)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatusLabel("Failed to start: ${e.message}")
                    binding.buttonStartNode.isEnabled = true
                    Toast.makeText(
                        this@MainActivity,
                        "Bootstrap failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun observeNodeStartup(paths: NodeBootstrapper.NodePaths) {
        lifecycleScope.launch {
            var lastStatus = "UNKNOWN"
            repeat(15) {
                lastStatus = withContext(Dispatchers.IO) { nodeController.statusText() }
                updateStatusLabel(lastStatus)

                val statusUpper = lastStatus.uppercase()
                if (statusUpper == "RUNNING") {
                    binding.buttonStartNode.isEnabled = false
                    binding.buttonStartNode.text = "Node running"
                    binding.textSteps.text =
                        "Node is running in the background. Config: ${paths.configFile.absolutePath}\nLogs: ${paths.logsDir.absolutePath}\nLeave the app open for initial sync."
                    Toast.makeText(
                        this@MainActivity,
                        "Node service started. Status: $lastStatus",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                delay(1_000)
            }

            binding.buttonStartNode.isEnabled = true
            binding.buttonStartNode.text = "Retry start"
            binding.textSteps.text =
                "Node did not report RUNNING. Status: $lastStatus\nCheck that the digibyted asset is present and review logs at ${paths.logsDir.absolutePath}."
            Toast.makeText(
                this@MainActivity,
                "Node status did not reach RUNNING (last: $lastStatus)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun DigiMobileNodeController.statusText(): String {
        return try {
            val status = getStatus()?.trim()
            if (status.isNullOrBlank()) "UNKNOWN" else status
        } catch (e: UnsatisfiedLinkError) {
            // TODO: surface a clearer error if the JNI library is missing or incompatible.
            "JNI missing"
        }
    }

    private fun updateStatusLabel(status: String) {
        binding.textStatus.text = "Status: $status"
    }
}
