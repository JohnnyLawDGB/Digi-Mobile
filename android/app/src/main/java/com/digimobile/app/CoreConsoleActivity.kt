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
import java.io.File
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CoreConsoleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCoreConsoleBinding
    private lateinit var nodeManager: NodeManager
    private lateinit var bootstrapper: NodeBootstrapper

    private val consoleEntries = mutableListOf<String>()
    private val commandHistory = ArrayDeque<String>()
    private var lastNodeState: NodeState = NodeState.Idle

    companion object {
        private const val HISTORY_LIMIT = 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoreConsoleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nodeManager = NodeManagerProvider.get(applicationContext)
        bootstrapper = NodeBootstrapper(applicationContext)

        binding.textConsole.text = "digibyte-cli console ready."

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
                    lastNodeState = state
                    binding.textStatus.text = "Node status: ${state.toUserMessage()}"
                    if (state is NodeState.Idle || state is NodeState.Error) {
                        showWarning("Node is not running. Restart it from the main screen.")
                        setCommandInputEnabled(false)
                    } else if (state !is NodeState.Ready) {
                        showWarning("Node must be running and synced before sending commands.")
                        setCommandInputEnabled(false)
                    } else {
                        hideWarning()
                        setCommandInputEnabled(true)
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
        if (lastNodeState !is NodeState.Ready) {
            showWarning("Node is not ready yet. Wait for full sync before sending commands.")
            return
        }

        hideWarning()
        binding.buttonSend.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { runCliCommand(commandText) }
                appendConsoleEntry(result)
            } finally {
                binding.buttonSend.isEnabled = true
                binding.inputCommand.text?.clear()
            }
        }
    }

    private data class CliResult(val command: String, val stdout: String, val stderr: String, val exitCode: Int)

    private fun runCliCommand(command: String): CliResult {
        val paths = bootstrapper.ensureBootstrap()
        val cliBinary = File(filesDir, "bin/digibyte-cli")
        if (!cliBinary.exists()) {
            return CliResult(command, "", "digibyte-cli not found at ${cliBinary.absolutePath}", -1)
        }

        val confArg = "-conf=${paths.configFile.absolutePath}"
        val datadirArg = "-datadir=${paths.dataDir.absolutePath}"
        val args = listOf(cliBinary.absolutePath, confArg, datadirArg) + parseCommand(command)

        return runCatching {
            val process = ProcessBuilder(args).start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            CliResult(command, stdout.trim(), stderr.trim(), exitCode)
        }.getOrElse { error ->
            CliResult(command, "", "Failed to run command: ${error.message}", -1)
        }
    }

    private fun parseCommand(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val regex = Regex("""[^\s\"']+|\"[^\"]*\"|'[^']*'""")
        return regex.findAll(raw).map { matchResult ->
            matchResult.value.trim().trim { ch -> ch == '"' || ch == '\'' }
        }.toList()
    }

    private fun appendConsoleEntry(result: CliResult) {
        if (commandHistory.size >= HISTORY_LIMIT) {
            commandHistory.removeFirst()
        }
        commandHistory.addLast(result.command)

        val entryBuilder = StringBuilder()
        entryBuilder.appendLine(">$ ${result.command}")
        if (result.stdout.isNotEmpty()) {
            entryBuilder.appendLine(result.stdout)
        }
        if (result.stderr.isNotEmpty()) {
            entryBuilder.appendLine("[stderr] ${result.stderr}")
        }
        entryBuilder.append("(exit ${result.exitCode})")

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

    private fun showWarning(message: String) {
        binding.textWarning.isVisible = true
        binding.textWarning.text = message
    }

    private fun hideWarning() {
        binding.textWarning.isVisible = false
        binding.textWarning.text = ""
    }
}
