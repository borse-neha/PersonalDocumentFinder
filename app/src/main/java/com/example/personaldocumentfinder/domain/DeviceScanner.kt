package com.example.personaldocumentfinder.domain

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class DiscoveredCandidate(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val fileSize: Long,
    val filePath: String,
    val dateModified: Long
)

class DeviceScanner(private val context: Context) {

    private val supportedExtensions = setOf(
        "pdf", "jpg", "jpeg", "png", "txt", "doc", "docx"
    )

    fun scanSharedStorage(batchSize: Int = 20): Flow<List<DiscoveredCandidate>> = flow {
        val discoveredBatch = mutableListOf<DiscoveredCandidate>()
        val seenPaths = mutableSetOf<String>()
        val seenUris = mutableSetOf<String>()

        // 1. Unified MediaStore Query
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"

        val selectionArgs = arrayOf(
            "image/%",
            "text/%",
            "application/pdf",
            "application/msword",
            "%.pdf",
            "%.docx"
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Document_$id"
                    val mimeType = cursor.getString(mimeColumn) ?: getMimeTypeFromExtension(name)
                    val size = cursor.getLong(sizeColumn)
                    val filePath = cursor.getString(dataColumn) ?: ""
                    val dateModified = cursor.getLong(dateColumn) * 1000L

                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in supportedExtensions || mimeType.startsWith("image/") || mimeType == "application/pdf") {
                        val contentUri = ContentUris.withAppendedId(collection, id)
                        val uriString = contentUri.toString()

                        if (size > 0 && seenUris.add(uriString) && (filePath.isBlank() || seenPaths.add(filePath))) {
                            discoveredBatch.add(
                                DiscoveredCandidate(
                                    uri = contentUri,
                                    name = name,
                                    mimeType = mimeType,
                                    fileSize = size,
                                    filePath = filePath,
                                    dateModified = dateModified
                                )
                            )

                            if (discoveredBatch.size >= batchSize) {
                                emit(discoveredBatch.toList())
                                discoveredBatch.clear()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Secondary Direct Folder Check (For files not indexed by MediaStore yet when All Files Access is granted)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            val rootExternal = Environment.getExternalStorageDirectory()
            val targetDirs = listOf(
                File(rootExternal, "Download"),
                File(rootExternal, "Documents"),
                File(rootExternal, "Pictures"),
                File(rootExternal, "DCIM"),
                File(rootExternal, "Android/media/com.whatsapp/WhatsApp/Media")
            )

            for (dir in targetDirs) {
                if (dir.exists() && dir.canRead()) {
                    val files = dir.walkTopDown().maxDepth(3).filter { file ->
                        file.isFile && file.length() > 0 && file.extension.lowercase() in supportedExtensions
                    }

                    for (file in files) {
                        if (seenPaths.add(file.absolutePath)) {
                            val fileUri = Uri.fromFile(file)
                            if (seenUris.add(fileUri.toString())) {
                                discoveredBatch.add(
                                    DiscoveredCandidate(
                                        uri = fileUri,
                                        name = file.name,
                                        mimeType = getMimeTypeFromExtension(file.extension),
                                        fileSize = file.length(),
                                        filePath = file.absolutePath,
                                        dateModified = file.lastModified()
                                    )
                                )

                                if (discoveredBatch.size >= batchSize) {
                                    emit(discoveredBatch.toList())
                                    discoveredBatch.clear()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Emit remaining items
        if (discoveredBatch.isNotEmpty()) {
            emit(discoveredBatch.toList())
            discoveredBatch.clear()
        }
    }.flowOn(Dispatchers.IO)

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
