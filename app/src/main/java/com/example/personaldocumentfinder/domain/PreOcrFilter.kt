package com.example.personaldocumentfinder.domain

object PreOcrFilter {

    private val documentFilenameHints = setOf(
        "doc", "document", "scan", "scanned", "receipt", "invoice", "ticket",
        "certificate", "aadhaar", "aadhar", "pan", "passport", "bill",
        "form", "statement", "payslip", "hallticket", "hall_ticket", "marksheet",
        "rc", "puc", "license", "licence", "tax", "admit", "degree", "diploma",
        "bonafide", "transcript", "salary", "medical", "insurance", "cheque", "passbook"
    )

    fun isPlausibleCandidate(
        name: String,
        mimeType: String,
        fileSize: Long,
        filePath: String
    ): Boolean {
        if (fileSize <= 0) return false

        val lowerName = name.lowercase().trim()
        val lowerPath = filePath.lowercase().trim()

        // 1. PDF / Office Docs are always plausible
        val isPdfOrDoc = mimeType == "application/pdf" ||
                lowerName.endsWith(".pdf") ||
                lowerName.endsWith(".doc") ||
                lowerName.endsWith(".docx") ||
                mimeType.startsWith("text/")

        if (isPdfOrDoc) return true

        // 2. Explicit filename or path document hints
        val hasFilenameHint = documentFilenameHints.any { keyword ->
            lowerName.contains(keyword) || lowerPath.contains(keyword)
        }
        if (hasFilenameHint) return true

        // 3. Document screenshots
        val isScreenshot = lowerName.contains("screenshot") || lowerName.contains("screen_shot") || lowerPath.contains("screenshots")
        if (isScreenshot && fileSize >= 15 * 1024L) return true

        // 4. Files in Download or Documents folder
        val isInDocumentFolder = lowerPath.contains("/download/") || lowerPath.contains("/documents/") || lowerPath.contains("/scans/")
        if (isInDocumentFolder) return true

        // 5. Standard camera photo patterns without document hints
        val isCameraPhotoPattern = lowerName.startsWith("img_") ||
                lowerName.startsWith("dcim_") ||
                lowerName.startsWith("pxl_") ||
                lowerName.startsWith("photo_") ||
                lowerName.startsWith("pic_")

        if (isCameraPhotoPattern && !hasFilenameHint && !isScreenshot) {
            return false
        }

        // Default for remaining images: plausible if size is reasonably large
        return mimeType.startsWith("image/") && fileSize >= 20 * 1024L
    }
}
