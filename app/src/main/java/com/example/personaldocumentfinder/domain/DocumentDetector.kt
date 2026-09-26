package com.example.personaldocumentfinder.domain

data class DetectionResult(
    val isDocument: Boolean,
    val documentConfidence: Float,
    val detectionReason: String
)

object DocumentDetector {

    private val documentFilenameKeywords = setOf(
        "doc", "document", "scan", "scanned", "receipt", "invoice", "ticket",
        "certificate", "aadhaar", "aadhar", "pan", "passport", "bill",
        "form", "statement", "payslip", "hallticket", "hall_ticket", "marksheet",
        "rc", "puc", "license", "licence", "tax"
    )

    private val documentContentKeywords = setOf(
        "aadhaar", "aadhar", "government of india", "govt of india", "permanent account number",
        "income tax", "hall ticket", "university", "examination", "roll no", "seat no",
        "tax invoice", "amount paid", "bank statement", "transaction", "subtotal",
        "registration certificate", "chassis no", "engine no", "salary slip",
        "employee id", "prescription", "medical report", "utility bill"
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
                isDocument = true,
                documentConfidence = 0.95f,
                detectionReason = "Document MIME type ($mimeType)"
            )
        }

        // 2. Check filename hints
        val hasFilenameHint = documentFilenameKeywords.any { keyword ->
            lowerName.contains(keyword)
        }

        // 3. Check OCR text characteristics
        val textLength = lowerText.length
        val wordCount = if (lowerText.isBlank()) 0 else lowerText.split(Regex("\\s+")).size
        val hasContentKeyword = documentContentKeywords.any { keyword ->
            lowerText.contains(keyword)
        }

        var confidence = 0.0f

        if (hasContentKeyword) {
            confidence += 0.6f
        }

        if (textLength >= 30 && wordCount >= 5) {
            confidence += 0.3f
        } else if (textLength >= 15 && wordCount >= 3) {
            confidence += 0.15f
        }

        if (hasFilenameHint) {
            confidence += 0.25f
        }

        // Screenshot containing receipt / document content
        val isScreenshot = lowerName.contains("screenshot") || lowerName.contains("screen_shot")
        if (isScreenshot && (hasContentKeyword || wordCount >= 4)) {
            confidence += 0.3f
        }

        // Check if photograph without text (e.g. IMG_1234.jpg with < 10 chars of text)
        val isCameraPhotoPattern = lowerName.startsWith("img_") ||
                lowerName.startsWith("dcim_") ||
                lowerName.startsWith("pxl_") ||
                lowerName.startsWith("photo_")

        if (isCameraPhotoPattern && wordCount < 3 && !hasContentKeyword && !hasFilenameHint) {
            confidence -= 0.4f
        }

        confidence = confidence.coerceIn(0.0f, 1.0f)
        val isDocument = confidence >= 0.30f

        val reason = when {
            isDocument && hasContentKeyword -> "High-confidence document content keywords detected"
            isDocument && isScreenshot -> "Document content screenshot detected"
            isDocument -> "Text density & layout indicators matched document pattern"
            else -> "Photograph / No document text detected (Rejected)"
        }

        return DetectionResult(
            isDocument = isDocument,
            documentConfidence = confidence,
            detectionReason = reason
        )
    }
}
