package com.digimobile.node

import java.io.File

object NodeDiagnostics {

    fun hasConfig(): Boolean {
        val paths = NodeEnvironment.paths ?: return false
        return paths.configFile.exists()
    }

    fun configPath(): File? = NodeEnvironment.paths?.configFile

    fun getDebugLogFile(datadir: File): File = File(datadir, "debug.log")

    fun tailDebugLog(datadir: File, maxLines: Int = 100): List<String> {
        if (maxLines <= 0) return emptyList()

        return runCatching {
            val debugLog = getDebugLogFile(datadir)
            if (!debugLog.exists() || !debugLog.canRead()) return emptyList()

            debugLog.bufferedReader().useLines { lines ->
                val buffer = ArrayDeque<String>()
                lines.forEach { line ->
                    if (buffer.size == maxLines) {
                        buffer.removeFirst()
                    }
                    buffer.addLast(line)
                }
                buffer.toList()
            }
        }.getOrElse { emptyList() }
    }

    fun tailDebugLog(maxLines: Int = 100): List<String> {
        val paths = NodeEnvironment.paths ?: return emptyList()
        return tailDebugLog(paths.dataDir, maxLines)
    }
}
