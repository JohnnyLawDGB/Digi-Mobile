package com.digimobile.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.digimobile.node.DigiMobileNodeController
import com.digimobile.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
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

        binding.buttonStartNode.setOnClickListener {
            startNodeFlow()
        }

        updateStatusLabel(nodeController.statusText())
    }

    private fun startNodeFlow() {
        binding.buttonStartNode.isEnabled = false
        updateStatusLabel("Preparing node...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val paths = bootstrapper.ensureBootstrap()
                // TODO: bundle the DigiByte daemon binary via assets or native libs and ensure it exists here.
                nodeController.startNode(paths.configFile.absolutePath, paths.dataDir.absolutePath)

                val status = nodeController.statusText()
                withContext(Dispatchers.Main) {
                    updateStatusLabel(status)
                    Toast.makeText(
                        this@MainActivity,
                        "Node start requested. Status: $status",
                        Toast.LENGTH_LONG
                    ).show()
                }
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

    private fun DigiMobileNodeController.statusText(): String {
        return try {
            val status = getStatus()
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
