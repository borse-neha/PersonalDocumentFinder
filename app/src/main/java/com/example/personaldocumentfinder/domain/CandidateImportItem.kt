package com.example.personaldocumentfinder.domain

import android.net.Uri

data class CandidateImportItem(
    val uri: Uri,
    val fileName: String,
    val classification: ClassificationResult,
    val selectedCategory: String = classification.category,
    val isDuplicate: Boolean = false
)
