package com.digimobile.app

import android.content.Context
import android.content.SharedPreferences
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

class ChainstateBootstrapper(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun extractSnapshotIfNeeded(
        datadir: File,
        onProgress: (Int) -> Unit,
        onLog: (String) -> Unit
    ): Boolean {
        datadir.mkdirs()
        val externalSnapshot = context.getExternalFilesDir(null)?.let { File(it, SNAPSHOT_FILENAME) }
        val snapshotFile = when {
            externalSnapshot?.exists() == true -> externalSnapshot
            else -> File(context.filesDir, SNAPSHOT_FILENAME)
        }

        if (!snapshotFile.exists()) {
            return true
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

        val chainstateDir = File(datadir, "chainstate")
        if (chainstateDir.exists()) {
            deleteRecursively(chainstateDir)
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

        return runCatching {
            FileInputStream(snapshotFile).use { fis ->
                GzipCompressorInputStream(BufferedInputStream(fis)).use { gzip ->
                    TarArchiveInputStream(gzip).use { tar ->
                        var entry = tar.nextTarEntry
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (entry != null) {
                            val name = entry.name
                            if (name.startsWith("chainstate/")) {
                                val outputFile = File(datadir, name)
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
            updateProgress()
            prefs.edit().putBoolean(KEY_SNAPSHOT_APPLIED, true).apply()
            true
        }.onFailure { error ->
            onLog("Failed to extract snapshot: ${error.message}")
        }.getOrElse { false }
    }

    fun isSnapshotApplied(): Boolean = prefs.getBoolean(KEY_SNAPSHOT_APPLIED, false)

    fun resetSnapshotFlag() {
        prefs.edit().putBoolean(KEY_SNAPSHOT_APPLIED, false).apply()
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

    companion object {
        const val SNAPSHOT_FILENAME = "dgb-chainstate-mainnet-h22595645-8.26.tar.gz"
        const val SNAPSHOT_SHA256 = "c1e12e9f0cdd2dbd04b6b6ca68a0d8f8e3c1a9b8ed54f05c9b4721517972ed85"
        const val SNAPSHOT_HEIGHT = 22_595_645
        const val SNAPSHOT_HASH = "00000000000000001310a0340b212b3c8e2d7c5e3f21a2c0c0e2f6d8d7e9a1b2"

        private const val PREFS_NAME = "digimobile_prefs"
        private const val KEY_SNAPSHOT_APPLIED = "snapshot_applied"
    }
}
