package com.digimobile.app

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.digimobile.app.databinding.ActivityCoreConsoleBinding
import com.digimobile.node.NodeManager
import com.digimobile.node.NodeManagerProvider
import com.digimobile.node.NodeState
import com.digimobile.node.toUserMessage
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CoreConsoleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCoreConsoleBinding
    private lateinit var nodeManager: NodeManager

    private val consoleEntries = mutableListOf<String>()
    private val commandHistory = ArrayDeque<String>()
    private var cliAvailable: Boolean = true

    companion object {
        private const val HISTORY_LIMIT = 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoreConsoleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nodeManager = NodeManagerProvider.get(applicationContext)

        cliAvailable = nodeManager.cliAvailable
        if (cliAvailable) {
            binding.textConsole.text = "digibyte-cli console ready."
        } else {
            showCliUnavailableMessage()
        }

        binding.buttonSend.setOnClickListener { onSendCommand() }
        binding.inputCommand.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSendCommand()
                return@OnEditorActionListener true
            }
            false
        })

        observeNodeState()
    }

    private fun observeNodeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                nodeManager.nodeState.collect { state ->
                    cliAvailable = nodeManager.cliAvailable
                    binding.textStatus.text = "Node status: ${state.toUserMessage(this@CoreConsoleActivity)}"
                    if (!cliAvailable) {
                        showCliUnavailableMessage()
                    } else {
                        val isNodeRunning =
                            state is NodeState.Ready || state is NodeState.Syncing || state is NodeState.ConnectingToPeers
                        if (!isNodeRunning) {
                            showWarning("Node is not running. Restart it from the main screen.")
                            setCommandInputEnabled(false)
                        } else {
                            hideWarning()
                            setCommandInputEnabled(true)
                        }
                    }
                }
            }
        }
    }

    private fun onSendCommand() {
        val commandText = binding.inputCommand.text.toString().trim()
        if (commandText.isEmpty()) {
            showWarning("Please enter a command to send.")
            return
        }
        cliAvailable = nodeManager.cliAvailable
        if (!cliAvailable) {
            showCliUnavailableMessage()
            return
        }

        hideWarning()
        binding.buttonSend.isEnabled = false

        lifecycleScope.launch {
            try {
                val args = parseCommand(commandText)
                val result = withContext(Dispatchers.IO) { nodeManager.executeCliCommand(args) }
                cliAvailable = nodeManager.cliAvailable
                if (!cliAvailable) {
                    showCliUnavailableMessage()
                    return@launch
                }
                appendConsoleEntry(commandText, result)
            } finally {
                binding.buttonSend.isEnabled = true
                binding.inputCommand.text?.clear()
            }
        }
    }

    private fun parseCommand(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val regex = Regex("""[^\s\"']+|\"[^\"]*\"|'[^']*'""")
        return regex.findAll(raw).map { matchResult ->
            matchResult.value.trim().trim { ch -> ch == '"' || ch == '\'' }
        }.toList()
    }

    private fun appendConsoleEntry(command: String, result: NodeManager.CliResult) {
        if (commandHistory.size >= HISTORY_LIMIT) {
            commandHistory.removeFirst()
        }
        commandHistory.addLast(command)

        val entryBuilder = StringBuilder()
        entryBuilder.appendLine(">$ $command")
        if (result.stdout.isNotEmpty()) {
            entryBuilder.appendLine(result.stdout)
        }
        if (result.stderr.isNotEmpty()) {
            entryBuilder.appendLine("[stderr] ${result.stderr}")
        }
        if (result.exitCode != 0) {
            val errorMessage = buildString {
                append("[error] digibyte-cli exited with code ${result.exitCode}")
                if (result.stderr.isNotEmpty()) {
                    append(": ${result.stderr}")
                }
            }
            entryBuilder.append(errorMessage)
        } else {
            entryBuilder.append("(exit ${result.exitCode})")
        }

        consoleEntries.add(entryBuilder.toString())
        binding.textConsole.text = consoleEntries.joinToString(separator = "\n\n")
        scrollToBottom()
    }

    private fun scrollToBottom() {
        binding.scrollConsole.post {
            binding.scrollConsole.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun setCommandInputEnabled(enabled: Boolean) {
        binding.buttonSend.isEnabled = enabled
        binding.inputCommand.isEnabled = enabled
    }

    private fun showCliUnavailableMessage() {
        cliAvailable = false
        binding.textConsole.text =
            "digibyte-cli is not available in this build of Digi-Mobile; the core console is disabled."
        showWarning("digibyte-cli is not available in this build of Digi-Mobile; the core console is disabled.")
        setCommandInputEnabled(false)
    }

    private fun showWarning(message: String) {
        binding.textWarning.isVisible = true
        binding.textWarning.text = message
    }

    private fun hideWarning() {
        binding.textWarning.isVisible = false
        binding.textWarning.text = ""
    }
}
