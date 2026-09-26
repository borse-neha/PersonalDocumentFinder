package com.example.personaldocumentfinder.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

class StorageManager(private val context: Context) {

    data class StorageStats(
        val totalBytes: Long,
        val fileCount: Int
    )

    data class CopyResult(
        val storedFileName: String,
        val internalPath: String,
        val fileSize: Long,
        val mimeType: String,
        val contentHash: String
    )

    fun copyFileToAppPrivateStorage(uri: Uri, category: String, customFileName: String? = null): CopyResult? {
        return try {
            val originalName = customFileName ?: getFileNameFromUri(uri)
            val sanitizedCategory = sanitizeCategory(category)
            val categoryDir = File(context.filesDir, sanitizedCategory)
            if (!categoryDir.exists()) {
                categoryDir.mkdirs()
            }

            val storedFileName = generateUniqueFileName(categoryDir, originalName)
            val destFile = File(categoryDir, storedFileName)

            var fileSize = 0L
            val digest = MessageDigest.getInstance("SHA-256")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        fileSize += bytesRead
                    }
                }
            } ?: return null

            val hashBytes = digest.digest()
            val contentHash = hashBytes.joinToString("") { "%02x".format(it) }
            val mimeType = context.contentResolver.getType(uri) ?: getMimeTypeFromExtension(destFile.extension)

            CopyResult(
                storedFileName = storedFileName,
                internalPath = destFile.absolutePath,
                fileSize = fileSize,
                mimeType = mimeType,
                contentHash = contentHash
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun moveFileToCategory(internalPath: String, newCategory: String): String? {
        return try {
            val currentFile = File(internalPath)
            if (!currentFile.exists()) return null

            val sanitizedCategory = sanitizeCategory(newCategory)
            val newCategoryDir = File(context.filesDir, sanitizedCategory)
            if (!newCategoryDir.exists()) {
                newCategoryDir.mkdirs()
            }

            val newFile = File(newCategoryDir, currentFile.name)
            if (currentFile.renameTo(newFile)) {
                newFile.absolutePath
            } else {
                // Fallback copy & delete
                currentFile.copyTo(newFile, overwrite = true)
                currentFile.delete()
                newFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteAppPrivateFile(internalPath: String): Boolean {
        return try {
            val file = File(internalPath)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getStorageStats(): StorageStats {
        var totalBytes = 0L
        var fileCount = 0

        fun calculateDir(dir: File) {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    calculateDir(file)
                } else if (file.isFile) {
                    totalBytes += file.length()
                    fileCount++
                }
            }
        }

        calculateDir(context.filesDir)
        return StorageStats(totalBytes = totalBytes, fileCount = fileCount)
    }

    fun getFileNameFromUri(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {}
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }
        return name.takeIf { !it.isNullOrBlank() } ?: "Document_${System.currentTimeMillis()}"
    }

    private fun sanitizeCategory(category: String): String {
        return category.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    private fun generateUniqueFileName(dir: File, originalName: String): String {
        var file = File(dir, originalName)
        if (!file.exists()) return originalName

        val nameWithoutExt = originalName.substringBeforeLast(".")
        val ext = originalName.substringAfterLast(".", "")
        val extPart = if (ext.isNotEmpty()) ".$ext" else ""

        var counter = 1
        while (file.exists()) {
            file = File(dir, "${nameWithoutExt}_$counter$extPart")
            counter++
        }
        return file.name
    }

    private fun getMimeTypeFromExtension(ext: String): String {
        return when (ext.lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "txt" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }
}
