package com.example.personaldocumentfinder.domain

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object FileOpener {

    sealed class OpenResult {
        object Success : OpenResult()
        data class Error(val message: String) : OpenResult()
    }

    fun openDocument(context: Context, internalPath: String, mimeType: String): OpenResult {
        val file = File(internalPath)
        if (!file.exists()) {
            return OpenResult.Error("Document file no longer exists in app storage.")
        }

        return try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            OpenResult.Success
        } catch (e: ActivityNotFoundException) {
            OpenResult.Error("No compatible app found to open this document type ($mimeType).")
        } catch (e: Exception) {
            OpenResult.Error("Failed to open document: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
