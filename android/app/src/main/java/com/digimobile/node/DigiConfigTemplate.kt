package com.digimobile.node

import android.util.Log
import java.io.File
import java.util.UUID

// Relevant config handling:
// - DigiConfigTemplate.ensureConfig(): creates/parses digibyte.conf in the node data directory.
// - NodeBootstrapper.ensureBootstrap(): calls this helper and records the resulting RPC credentials.
// - DigiMobileNodeController.startNode() / nativeStartNode(): consume the paths ensured here via -conf/-datadir.

data class RpcCredentials(val user: String, val password: String)

object DigiConfigTemplate {

    private const val TAG = "DigiConfigTemplate"
    private const val RPC_USER_DEFAULT = "digiuser"

    fun ensureConfig(datadir: File): RpcCredentials {
        if (!datadir.exists()) {
            datadir.mkdirs()
        }
        val configFile = File(datadir, "digibyte.conf")
        if (!configFile.exists()) {
            val credentials = RpcCredentials(RPC_USER_DEFAULT, generatePassword())
            configFile.writeText(buildTemplate(credentials))
            return credentials
        }

        val content = runCatching { configFile.readText() }.getOrElse { error ->
            Log.w(TAG, "Failed to read existing config: ${error.message}")
            ""
        }
        val parsedUser = parseValue(content, "rpcuser") ?: RPC_USER_DEFAULT
        val parsedPassword = parseValue(content, "rpcpassword")

        if (parsedPassword != null && content.contains("rpcuser=")) {
            return RpcCredentials(parsedUser, parsedPassword)
        }

        val credentials = RpcCredentials(parsedUser, parsedPassword ?: generatePassword())
        val builder = StringBuilder(content.trimEnd())
        if (!content.endsWith("\n")) {
            builder.append("\n")
        }
        if (!content.contains("rpcuser=")) {
            builder.append("rpcuser=${credentials.user}\n")
        }
        if (!content.contains("rpcpassword=")) {
            builder.append("rpcpassword=${credentials.password}\n")
        }
        configFile.writeText(builder.toString())
        return credentials
    }

    private fun buildTemplate(credentials: RpcCredentials): String {
        return """
            # DigiByte mobile default configuration
            server=1
            listen=1
            dns=1
            discover=1
            maxconnections=8
            prune=2048
            dbcache=128
            txindex=0

            rpcuser=${credentials.user}
            rpcpassword=${credentials.password}
            rpcallowip=127.0.0.1
            rpcbind=127.0.0.1
        """.trimIndent() + "\n"
    }

    private fun parseValue(contents: String, key: String): String? {
        return contents.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
    }

    private fun generatePassword(): String = UUID.randomUUID().toString()
}
