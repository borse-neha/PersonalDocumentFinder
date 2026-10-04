package com.example.personaldocumentfinder.domain

enum class DocumentDetectionState {
    DOCUMENT,
    NON_DOCUMENT,
    UNCERTAIN
}

data class DetectionResult(
    val state: DocumentDetectionState,
    val documentConfidence: Float,
    val detectionReason: String
) {
    val isDocument: Boolean
        get() = state == DocumentDetectionState.DOCUMENT
}

object DocumentDetector {

    private val multiWordPhrases = listOf(
        "government of india", "govt of india", "unique identification",
        "income tax department", "permanent account number", "hall ticket",
        "admit card", "examination board", "board of examination",
        "tax invoice", "amount paid", "bank statement", "transaction summary",
        "registration certificate", "chassis no", "engine no", "salary slip",
        "offer letter", "appointment letter", "medical report", "utility bill",
        "pollution under control", "public notice", "official announcement",
        "terms and conditions", "application form"
    )

    private val documentKeywords = setOf(
        "aadhaar", "aadhar", "passport", "marksheet", "transcript", "diploma",
        "gstin", "subtotal", "payslip", "pf number", "rc book", "puc", "prescription",
        "notification", "announcement", "certificate", "declaration"
    )

    private val singleAmbiguousKeywords = setOf(
        "tax", "bill", "license", "licence", "university", "college", "school",
        "receipt", "invoice", "statement", "certificate", "ticket", "form",
        "office", "student", "roll", "account", "amount", "total", "paid"
    )

    fun detect(
        ocrText: String,
        fileName: String,
        mimeType: String
    ): DetectionResult {
        val lowerText = ocrText.lowercase().trim()
        val lowerName = fileName.lowercase().trim()

        // 1. PDF / Office Docs are always documents
        val isPdfOrDoc = mimeType == "application/pdf" ||
                lowerName.endsWith(".pdf") ||
                lowerName.endsWith(".doc") ||
                lowerName.endsWith(".docx") ||
                mimeType.startsWith("text/")

        if (isPdfOrDoc) {
            return DetectionResult(
                state = DocumentDetectionState.DOCUMENT,
                documentConfidence = 0.95f,
                detectionReason = "Document MIME type ($mimeType)"
            )
        }

        // 2. OCR Text Token Analysis
        val tokens = lowerText.split(Regex("\\s+")).filter { it.length >= 2 }
        val wordCount = tokens.size

        // Photos with no or trivial text (< 3 words) are rejected as NON_DOCUMENT
        if (wordCount < 3) {
            return DetectionResult(
                state = DocumentDetectionState.NON_DOCUMENT,
                documentConfidence = 0.05f,
                detectionReason = "Photograph / Low text density (< 3 words)"
            )
        }

        // 3. Multi-Word Phrase Match (Strongest positive signal)
        val hasMultiWordPhrase = multiWordPhrases.any { phrase -> lowerText.contains(phrase) }
        if (hasMultiWordPhrase) {
            return DetectionResult(
                state = DocumentDetectionState.DOCUMENT,
                documentConfidence = 0.90f,
                detectionReason = "Strong document keyphrase detected in content"
            )
        }

        // 4. Exact Document Keyword Count
        var documentKeywordMatches = 0
        documentKeywords.forEach { keyword ->
            if (lowerText.contains(keyword) || lowerName.contains(keyword)) {
                documentKeywordMatches++
            }
        }

        var ambiguousKeywordMatches = 0
        singleAmbiguousKeywords.forEach { keyword ->
            if (lowerText.contains(keyword) || lowerName.contains(keyword)) {
                ambiguousKeywordMatches++
            }
        }

        // Multi-keyword check (At least 2 ambiguous keywords or 1 exact document keyword)
        if (documentKeywordMatches >= 1 || ambiguousKeywordMatches >= 2) {
            return DetectionResult(
                state = DocumentDetectionState.DOCUMENT,
                documentConfidence = 0.80f,
                detectionReason = "Multiple document terminology signals matched"
            )
        }

        // 5. Screenshot Document Layout Check
        val isScreenshot = lowerName.contains("screenshot") || lowerName.contains("screen_shot")
        val hasDateOrNumberPattern = lowerText.contains(Regex("\\d{2}[/-]\\d{2}[/-]\\d{2,4}")) ||
                lowerText.contains(Regex("\\b\\d{4,12}\\b"))

        if (isScreenshot && wordCount >= 6 && (hasDateOrNumberPattern || ambiguousKeywordMatches >= 1)) {
            return DetectionResult(
                state = DocumentDetectionState.DOCUMENT,
                documentConfidence = 0.75f,
                detectionReason = "Document content screenshot detected"
            )
        }

        // 6. Camera Photo Pattern with single ambiguous word or general text
        val isCameraPhotoPattern = lowerName.startsWith("img_") ||
                lowerName.startsWith("dcim_") ||
                lowerName.startsWith("pxl_") ||
                lowerName.startsWith("photo_")

        if (isCameraPhotoPattern) {
            // A camera photo with only 1 ambiguous word (e.g. "bill" on a cereal box) is NOT a document
            if (ambiguousKeywordMatches <= 1 && wordCount < 15) {
                return DetectionResult(
                    state = DocumentDetectionState.NON_DOCUMENT,
                    documentConfidence = 0.15f,
                    detectionReason = "Casual photograph with incidental text (Rejected)"
                )
            }
        }

        // 7. General Text Density Check
        if (wordCount >= 15 && hasDateOrNumberPattern) {
            return DetectionResult(
                state = DocumentDetectionState.DOCUMENT,
                documentConfidence = 0.65f,
                detectionReason = "Structured text & numerical fields detected"
            )
        }

        // 8. Uncertain Document Evidence
        if (wordCount >= 10 || ambiguousKeywordMatches >= 1) {
            return DetectionResult(
                state = DocumentDetectionState.UNCERTAIN,
                documentConfidence = 0.45f,
                detectionReason = "Ambiguous document signals (Candidate Review Required)"
            )
        }

        // Default: Non-Document Photo
        return DetectionResult(
            state = DocumentDetectionState.NON_DOCUMENT,
            documentConfidence = 0.10f,
            detectionReason = "Non-document photograph / Casual image (Rejected)"
        )
    }
}
