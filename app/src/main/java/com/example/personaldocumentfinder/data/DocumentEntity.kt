package com.example.personaldocumentfinder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalName: String,
    val storedFileName: String,
    val mimeType: String,
    val originalUri: String,
    val internalPath: String,
    val category: String, // College, Identity, Finance, Office, Vehicle, Personal, Other Documents
    val documentType: String, // e.g. "Aadhaar Card", "Hall Ticket", "Receipt", "Unclassified Document"
    val documentConfidence: Float = 0.0f,
    val categoryConfidence: Float = 0.0f,
    val ocrText: String = "",
    val fileSize: Long = 0L,
    val dateImported: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isReviewed: Boolean = true,
    val contentHash: String = ""
)
