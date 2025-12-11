package com.digimobile.node

import android.util.Log
import com.digimobile.node.NodeConfigOptions
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

    fun ensureConfig(datadir: File, options: NodeConfigOptions): RpcCredentials {
        if (!datadir.exists()) {
            datadir.mkdirs()
        }
        val configFile = File(datadir, "digibyte.conf")
        val content = runCatching { configFile.readText() }.getOrElse { error ->
            Log.w(TAG, "Failed to read existing config: ${error.message}")
            ""
        }
        val parsedUser = parseValue(content, "rpcuser") ?: RPC_USER_DEFAULT
        val parsedPassword = parseValue(content, "rpcpassword")
        val credentials = RpcCredentials(parsedUser, parsedPassword ?: generatePassword())

        configFile.writeText(buildTemplate(credentials, options))
        return credentials
    }

    private fun buildTemplate(credentials: RpcCredentials, options: NodeConfigOptions): String {
        val builder = StringBuilder()
        builder.appendLine("# DigiByte mobile configuration")
        builder.appendLine("# Profile: ${options.preset}")
        builder.appendLine("server=1")
        builder.appendLine("listen=1")
        builder.appendLine("dns=1")
        builder.appendLine("discover=1")
        builder.appendLine("maxconnections=${options.maxConnections}")
        builder.appendLine("prune=${options.pruneTargetMb}")
        builder.appendLine("dbcache=${options.dbCacheMb}")
        builder.appendLine("txindex=0")
        if (options.blocksonly) {
            builder.appendLine("blocksonly=1")
        }
        builder.appendLine()
        builder.appendLine("rpcuser=${credentials.user}")
        builder.appendLine("rpcpassword=${credentials.password}")
        builder.appendLine("rpcallowip=127.0.0.1")
        builder.appendLine("rpcbind=127.0.0.1")
        return builder.toString()
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
