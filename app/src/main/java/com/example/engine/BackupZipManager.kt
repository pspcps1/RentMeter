package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.data.repository.BackupSnapshot
import com.example.data.repository.RentLedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupZipManager(
    private val context: Context,
    private val repository: RentLedgerRepository
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportToUri(destUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = repository.exportAllDataSnapshot()
            val payloadJson = json.encodeToString(snapshot)

            val outputStream = context.contentResolver.openOutputStream(destUri)
                ?: throw IllegalStateException("Unable to open destination file")

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                // 1. Write JSON database & settings snapshot
                zipOut.putNextEntry(ZipEntry("rentledger_backup.json"))
                zipOut.write(payloadJson.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // 2. Include stored meter & ID photos from filesDir/media
                val mediaRoot = File(context.filesDir, "media")
                if (mediaRoot.exists()) {
                    mediaRoot.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relPath = file.relativeTo(context.filesDir).path.replace("\\", "/")
                        zipOut.putNextEntry(ZipEntry(relPath))
                        FileInputStream(file).use { it.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
                }
            }
            "Backup exported (${snapshot.properties.size} properties, ${snapshot.invoices.size} invoices)"
        }
    }

    suspend fun importFromUri(sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Unable to open backup file")

            var snapshotJson: String? = null
            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        if (name == "rentledger_backup.json") {
                            snapshotJson = zipIn.readBytes().toString(Charsets.UTF_8)
                        } else if (name.startsWith("media/")) {
                            val targetFile = File(context.filesDir, name)
                            // Guard against zip path traversal
                            if (targetFile.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { fos ->
                                    zipIn.copyTo(fos)
                                }
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            val rawJson = snapshotJson
                ?: throw IllegalArgumentException("Invalid backup archive: rentledger_backup.json not found")
            val snapshot = json.decodeFromString<BackupSnapshot>(rawJson)
            repository.restoreFromSnapshot(snapshot)
            "Restored ${snapshot.properties.size} properties & ${snapshot.units.size} units"
        }
    }
}
