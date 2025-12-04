package com.digimobile.node

import java.io.File

object NodeDiagnostics {

    fun hasConfig(): Boolean {
        val paths = NodeEnvironment.paths ?: return false
        return paths.configFile.exists()
    }

    fun configPath(): File? = NodeEnvironment.paths?.configFile

    fun tailDebugLog(maxLines: Int = 100): List<String> {
        val debugLog = NodeEnvironment.paths?.debugLogFile ?: return emptyList()
        if (!debugLog.exists()) return emptyList()
        return debugLog.useLines { lines -> lines.toList().takeLast(maxLines) }
    }
}
