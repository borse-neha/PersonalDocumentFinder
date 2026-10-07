package com.example.personaldocumentfinder.domain

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

open class StorageManager(private val context: Context) {

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

    open fun calculateContentHash(uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            } ?: return null
            val hashBytes = digest.digest()
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    open fun getOrganizedBaseDir(): File {
        return try {
            val documentsPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val baseAppDir = File(documentsPublicDir, "Personal Document Finder")
            if (baseAppDir.exists() || baseAppDir.mkdirs()) {
                baseAppDir
            } else {
                getFallbackDir()
            }
        } catch (_: Throwable) {
            getFallbackDir()
        }
    }

    private fun getFallbackDir(): File {
        return try {
            val extFiles = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (extFiles != null) {
                val dir = File(extFiles, "Personal Document Finder")
                if (dir.exists() || dir.mkdirs()) return dir
            }
            val internal = File(context.filesDir, "Documents/Personal Document Finder")
            if (!internal.exists()) internal.mkdirs()
            internal
        } catch (_: Throwable) {
            val internal = File(context.filesDir, "Documents/Personal Document Finder")
            if (!internal.exists()) internal.mkdirs()
            internal
        }
    }

    fun getCategoryDir(category: String): File {
        val sanitizedCategory = sanitizeCategory(category)
        val dir = File(getOrganizedBaseDir(), sanitizedCategory)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun copyFileToOrganizedStorage(uri: Uri, category: String, customFileName: String? = null): CopyResult? {
        return try {
            val originalName = customFileName ?: getFileNameFromUri(uri)
            val categoryDir = getCategoryDir(category)

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

            if (fileSize <= 0L) {
                if (destFile.exists()) destFile.delete()
                return null
            }

            val hashBytes = digest.digest()
            val contentHash = hashBytes.joinToString("") { "%02x".format(it) }
            val mimeType = context.contentResolver.getType(uri) ?: getMimeTypeFromExtension(destFile.extension)

            // Notify MediaScanner so the organized file is immediately discoverable in phone's Files app
            try {
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
            } catch (_: Throwable) {}

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

    // For backwards compatibility with existing callers
    fun copyFileToAppPrivateStorage(uri: Uri, category: String, customFileName: String? = null): CopyResult? {
        return copyFileToOrganizedStorage(uri, category, customFileName)
    }

    fun moveFileToCategory(internalPath: String, newCategory: String): String? {
        return try {
            val currentFile = File(internalPath)
            if (!currentFile.exists()) return null

            val newCategoryDir = getCategoryDir(newCategory)
            if (currentFile.parentFile?.canonicalPath == newCategoryDir.canonicalPath) {
                return currentFile.absolutePath
            }

            val newStoredFileName = generateUniqueFileName(newCategoryDir, currentFile.name)
            val newFile = File(newCategoryDir, newStoredFileName)

            val success = if (currentFile.renameTo(newFile)) {
                true
            } else {
                currentFile.copyTo(newFile, overwrite = true)
                currentFile.delete()
                true
            }

            if (success) {
                try {
                    MediaScannerConnection.scanFile(context, arrayOf(newFile.absolutePath, currentFile.absolutePath), null, null)
                } catch (_: Throwable) {}
                newFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteAppPrivateFile(internalPath: String): Boolean {
        return try {
            val file = File(internalPath)
            if (!file.exists()) return true

            // Strict boundary safety check:
            // Ensure the file is strictly inside the app's organized storage or internal files dir.
            // Under NO circumstance should any external user directory outside our managed folders be deleted!
            val canonicalPath = file.canonicalPath
            val baseOrganized = getOrganizedBaseDir().canonicalPath
            val internalFiles = context.filesDir.canonicalPath
            val extFiles = context.getExternalFilesDir(null)?.canonicalPath

            val isAllowed = canonicalPath.startsWith(baseOrganized) ||
                    canonicalPath.startsWith(internalFiles) ||
                    (extFiles != null && canonicalPath.startsWith(extFiles))

            if (!isAllowed) {
                // Reject deletion outside app boundary to protect external user data!
                return false
            }

            val deleted = file.delete()
            try {
                MediaScannerConnection.scanFile(context, arrayOf(internalPath), null, null)
            } catch (_: Throwable) {}
            deleted
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

        calculateDir(getOrganizedBaseDir())
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
        return when (category.trim()) {
            "College", "Identity", "Finance", "Office", "Vehicle", "Personal" -> category.trim()
            "Other", "Other Documents" -> "Other Documents"
            else -> category.trim().replace(Regex("[^a-zA-Z0-9_ -]"), "_")
        }
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
