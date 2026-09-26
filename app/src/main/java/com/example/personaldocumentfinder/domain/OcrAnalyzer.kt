package com.example.personaldocumentfinder.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object OcrAnalyzer {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractText(context: Context, uri: Uri, mimeType: String): String {
        return withContext(Dispatchers.IO) {
            try {
                when {
                    mimeType.startsWith("image/") -> {
                        extractFromImageUri(context, uri)
                    }
                    mimeType == "application/pdf" -> {
                        extractFromPdfUri(context, uri)
                    }
                    mimeType.startsWith("text/") -> {
                        extractFromTextUri(context, uri)
                    }
                    else -> {
                        extractFromImageUri(context, uri)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
    }

    suspend fun extractFromBitmap(bitmap: Bitmap): String {
        return withContext(Dispatchers.IO) {
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = recognizer.process(inputImage).await()
                visionText.text.orEmpty()
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
    }

    private suspend fun extractFromImageUri(context: Context, uri: Uri): String {
        return try {
            val inputImage = InputImage.fromFilePath(context, uri)
            val visionText = recognizer.process(inputImage).await()
            visionText.text.orEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private suspend fun extractFromPdfUri(context: Context, uri: Uri): String {
        val extractedText = StringBuilder()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                val pageCount = minOf(renderer.pageCount, 5)

                for (i in 0 until pageCount) {
                    renderer.openPage(i).use { page ->
                        val width = page.width
                        val height = page.height
                        val bitmap = Bitmap.createBitmap(
                            minOf(width, 1080),
                            minOf(height, 1920),
                            Bitmap.Config.ARGB_8888
                        )
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val pageText = extractFromBitmap(bitmap)
                        if (pageText.isNotBlank()) {
                            extractedText.append(pageText).append("\n\n")
                        }
                        bitmap.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }

        return extractedText.toString().trim()
    }

    private fun extractFromTextUri(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
