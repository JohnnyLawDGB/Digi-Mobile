package com.digimobile.app

import android.content.Context
import android.content.SharedPreferences
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

class ChainstateBootstrapper(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun extractSnapshotIfNeeded(
        datadir: File,
        onProgress: (Int) -> Unit,
        onLog: (String) -> Unit
    ): Boolean {
        datadir.mkdirs()
        val snapshotFile = getSnapshotFile()

        if (!snapshotFile.exists()) {
            val downloaded = runCatching { ensureSnapshotDownloaded(onProgress, onLog) }.getOrNull()
            if (downloaded == null) {
                return false
            }
        }

        return extractSnapshotInto(datadir, onProgress, onLog)
    }

    fun isSnapshotApplied(): Boolean = prefs.getBoolean(KEY_SNAPSHOT_APPLIED, false)

    fun resetSnapshotFlag() {
        prefs.edit().putBoolean(KEY_SNAPSHOT_APPLIED, false).apply()
    }

    suspend fun ensureSnapshotDownloaded(
        onProgress: (Int) -> Unit = {},
        onLog: (String) -> Unit
    ): File? {
        val snapshotFile = getSnapshotFile()

        if (snapshotFile.exists()) {
            val existingChecksum = computeSha256(snapshotFile)
            if (existingChecksum.equals(SNAPSHOT_SHA256, ignoreCase = true)) {
                return snapshotFile
            } else {
                onLog("Snapshot checksum mismatch, deleting file")
                snapshotFile.delete()
            }
        }

        val tempFile = File(snapshotFile.parentFile, "$SNAPSHOT_FILENAME.download")
        if (!tempFile.parentFile.exists()) tempFile.parentFile.mkdirs()

        onLog("Starting snapshot download...")
        onProgress(0)

        return runCatching {
            val urlConnection = URL(SNAPSHOT_URL).openConnection() as HttpURLConnection
            urlConnection.connectTimeout = 15_000
            urlConnection.readTimeout = 15_000
            urlConnection.connect()

            val contentLength = urlConnection.contentLengthLong.takeIf { it > 0 } ?: -1L

            BufferedInputStream(urlConnection.inputStream).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead = 0L
                    var read = input.read(buffer)
                    var lastProgress = -1
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (contentLength > 0) {
                            val percent = ((bytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                            if (percent != lastProgress) {
                                lastProgress = percent
                                onProgress(percent)
                            }
                        }
                        read = input.read(buffer)
                    }
                    onProgress(100)
                }
            }

            onLog("Snapshot download complete")

            val checksum = computeSha256(tempFile)
            if (!checksum.equals(SNAPSHOT_SHA256, ignoreCase = true)) {
                onLog("Snapshot checksum mismatch, deleting file")
                tempFile.delete()
                null
            } else if (tempFile.renameTo(snapshotFile)) {
                snapshotFile
            } else {
                tempFile.copyTo(snapshotFile, overwrite = true)
                tempFile.delete()
                snapshotFile
            }
        }.getOrElse {
            onLog("Failed to download snapshot: ${it.message}")
            tempFile.delete()
            null
        }
    }

    fun extractSnapshotInto(
        datadir: File,
        onProgress: (Int) -> Unit,
        onLog: (String) -> Unit = {}
    ): Boolean {
        datadir.mkdirs()
        val snapshotFile = getSnapshotFile()

        if (!snapshotFile.exists()) {
            onLog("Snapshot file missing; nothing to apply.")
            return false
        }

        if (prefs.getBoolean(KEY_SNAPSHOT_APPLIED, false)) {
            return true
        }

        val checksum = computeSha256(snapshotFile)
        if (!checksum.equals(SNAPSHOT_SHA256, ignoreCase = true)) {
            onLog(
                "Checksum mismatch for ${snapshotFile.name}: expected $SNAPSHOT_SHA256, found $checksum"
            )
            return false
        }

        val totalSize = snapshotFile.length().coerceAtLeast(1L)
        var bytesRead = 0L
        var lastProgress = -1

        fun updateProgress() {
            val percent = ((bytesRead * 100) / totalSize).toInt().coerceIn(0, 100)
            if (percent != lastProgress) {
                lastProgress = percent
                onProgress(percent)
            }
        }

        val tempRoot = File(context.cacheDir, "snapshot_extract_${System.currentTimeMillis()}")

        return runCatching {
            tempRoot.mkdirs()

            try {
                FileInputStream(snapshotFile).use { fis ->
                    GzipCompressorInputStream(BufferedInputStream(fis)).use { gzip ->
                        TarArchiveInputStream(gzip).use { tar ->
                            var entry = tar.nextTarEntry
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (entry != null) {
                                val relativePath = entry.name
                                    .trimStart('/')
                                    .split('/')
                                    .drop(1)
                                    .joinToString("/")

                                if (relativePath.isNotEmpty()) {
                                    val outputFile = File(tempRoot, relativePath)
                                    if (entry.isDirectory) {
                                        outputFile.mkdirs()
                                    } else {
                                        outputFile.parentFile?.mkdirs()
                                        FileOutputStream(outputFile).use { output ->
                                            var read = tar.read(buffer)
                                            while (read != -1) {
                                                output.write(buffer, 0, read)
                                                bytesRead += read
                                                updateProgress()
                                                read = tar.read(buffer)
                                            }
                                        }
                                    }
                                } else {
                                    var read = tar.read(buffer)
                                    while (read != -1) {
                                        bytesRead += read
                                        updateProgress()
                                        read = tar.read(buffer)
                                    }
                                }

                                entry = tar.nextTarEntry
                            }
                        }
                    }
                }

                val chainstateSource = File(tempRoot, "chainstate")
                val blocksSource = File(tempRoot, "blocks")

                if (!chainstateSource.exists() || !blocksSource.exists()) {
                    onLog("Snapshot is missing required directories.")
                    return false
                }

                val chainstateTarget = File(datadir, "chainstate")
                val blocksTarget = File(datadir, "blocks")

                if (chainstateTarget.exists()) deleteRecursively(chainstateTarget)
                if (blocksTarget.exists()) deleteRecursively(blocksTarget)

                chainstateTarget.parentFile?.mkdirs()
                blocksTarget.parentFile?.mkdirs()

                moveOrCopy(chainstateSource, chainstateTarget)
                moveOrCopy(blocksSource, blocksTarget)

                updateProgress()
                prefs.edit().putBoolean(KEY_SNAPSHOT_APPLIED, true).apply()
                true
            } finally {
                if (tempRoot.exists()) {
                    deleteRecursively(tempRoot)
                }
            }
        }.onFailure { error ->
            onLog("Failed to extract snapshot: ${error.message}")
        }.getOrElse { false }
    }

    private fun getSnapshotFile(): File {
        val dir = File(context.filesDir, "bootstrap")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, SNAPSHOT_FILENAME)
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            String.format("%02x", byte)
        }
    }

    private fun deleteRecursively(target: File) {
        if (target.isDirectory) {
            target.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        target.delete()
    }

    private fun moveOrCopy(source: File, target: File) {
        if (source.renameTo(target)) return
        source.copyRecursively(target, overwrite = true)
        deleteRecursively(source)
    }

    companion object {
        const val SNAPSHOT_URL: String =
            "https://github.com/JohnnyLawDGB/Digi-Mobile/releases/download/bootstrap-8.26-mainnet-h22595645/dgb-chainstate-mainnet-h22595645-8.26-full.tar.gz"

        const val SNAPSHOT_FILENAME: String = "dgb-chainstate-mainnet-h22595645-8.26-full.tar.gz"
        const val SNAPSHOT_SHA256 = "c9ee30e68b378a7919aae79eb8ff21725dab382cdac573700efd37e0f92a8c8d"
        const val SNAPSHOT_HEIGHT: Long = 22595645L
        const val SNAPSHOT_HASH: String =
            "c9ee30e68b378a7919aae79eb8ff21725dab382cdac573700efd37e0f92a8c8d"

        private const val PREFS_NAME = "digimobile_prefs"
        private const val KEY_SNAPSHOT_APPLIED = "snapshot_applied"
    }
}
