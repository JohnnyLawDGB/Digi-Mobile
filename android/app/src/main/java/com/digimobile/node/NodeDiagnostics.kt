package com.digimobile.node

import java.io.File

enum class DebugLogStatus { Disabled, Missing, Present }

data class DebugLogInsight(
    val status: DebugLogStatus,
    val file: File?
)

object NodeDiagnostics {

    fun hasConfig(): Boolean {
        val paths = NodeEnvironment.paths ?: return false
        return paths.configFile.exists()
    }

    fun configPath(): File? = NodeEnvironment.paths?.configFile

    fun getDebugLogFile(datadir: File): File = debugLogConfig(datadir).file ?: File(datadir, "debug.log")

    fun debugLogConfig(datadir: File): DebugLogInsight {
        val configFile = File(datadir, "digibyte.conf")
        val contents = runCatching { configFile.readText() }.getOrNull()
        val nodeBugLogValue = parseConfigValue(contents, "nodebuglogfile")?.lowercase()
        val debugLogValue = parseConfigValue(contents, "debuglogfile")

        val loggingDisabled = nodeBugLogValue == "1" || nodeBugLogValue == "true"
        if (loggingDisabled) {
            return DebugLogInsight(DebugLogStatus.Disabled, null)
        }

        val logFile = when {
            debugLogValue.isNullOrBlank() -> File(datadir, "debug.log")
            File(debugLogValue).isAbsolute -> File(debugLogValue)
            else -> File(datadir, debugLogValue)
        }

        val status = if (logFile.exists()) DebugLogStatus.Present else DebugLogStatus.Missing
        return DebugLogInsight(status, logFile)
    }

    fun tailDebugLog(datadir: File, maxLines: Int = 100): List<String> {
        if (maxLines <= 0) return emptyList()

        val insight = debugLogConfig(datadir)
        val debugLog = insight.file ?: return emptyList()
        if (insight.status == DebugLogStatus.Disabled) return emptyList()

        return runCatching {
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

    private fun parseConfigValue(contents: String?, key: String): String? {
        val normalized = contents ?: return null
        return normalized.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .firstOrNull { it.startsWith("$key=", ignoreCase = true) }
            ?.substringAfter("=")
    }
}
