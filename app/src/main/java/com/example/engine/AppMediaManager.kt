package com.example.engine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ImportedMediaFile(
    val filePath: String,
    val mimeType: String
)

data class CameraCaptureTarget(
    val file: File,
    val contentUri: Uri
)

object AppMediaManager {

    private fun mediaSubDir(context: Context, folder: String): File =
        File(context.filesDir, "media/$folder").apply { mkdirs() }

    /**
     * Resolves a stored file path on disk. If a backup was restored from another device where
     * `filesDir` had a different package/user prefix, falls back to resolving relative to `media/`.
     */
    fun resolveStoredFile(context: Context, storedPath: String?): File? {
        if (storedPath.isNullOrBlank()) return null
        val direct = File(storedPath)
        if (direct.exists()) return direct
        val marker = "/media/"
        val idx = storedPath.indexOf(marker)
        if (idx >= 0) {
            val rel = storedPath.substring(idx + 1) // starts with "media/..."
            val relocated = File(context.filesDir, rel)
            if (relocated.exists()) return relocated
        }
        return null
    }

    fun createCameraCaptureTarget(
        context: Context,
        folder: String,
        prefix: String
    ): CameraCaptureTarget {
        val dir = mediaSubDir(context, folder)
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return CameraCaptureTarget(file = file, contentUri = uri)
    }

    suspend fun copyUriToInternalMedia(
        context: Context,
        uri: Uri,
        folder: String,
        prefix: String
    ): ImportedMediaFile? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val detectedMime = resolver.getType(uri) ?: "image/jpeg"
            val ext = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(detectedMime)
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
                ?: if (detectedMime.contains("pdf", ignoreCase = true)) "pdf" else "jpg"

            val dir = mediaSubDir(context, folder)
            val dest = File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            ImportedMediaFile(
                filePath = dest.absolutePath,
                mimeType = detectedMime
            )
        }.getOrNull()
    }

    fun deleteStoredFileIfOwned(context: Context, storedPath: String?) {
        val file = resolveStoredFile(context, storedPath) ?: return
        runCatching {
            if (file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                file.delete()
            }
        }
    }

    fun openDocumentExternal(context: Context, storedPath: String, mimeType: String): Boolean {
        val file = resolveStoredFile(context, storedPath) ?: return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "Open document"))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun shareDocumentCopy(context: Context, storedPath: String, mimeType: String, title: String): Boolean {
        val file = resolveStoredFile(context, storedPath) ?: return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            val chooser = Intent.createChooser(intent, "Save or Share $title").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
