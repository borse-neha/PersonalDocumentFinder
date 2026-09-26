package com.example.personaldocumentfinder.domain

data class ClassificationResult(
    val isDocument: Boolean,
    val documentType: String,
    val category: String,
    val documentConfidence: Float,
    val categoryConfidence: Float,
    val ocrText: String = "",
    val detectionReason: String = ""
)

object DocumentClassifier {

    private val collegeKeywords = listOf(
        "hall ticket", "hallticket", "examination", "university", "college",
        "marksheet", "transcript", "admit card", "seat no", "roll no",
        "student", "semester", "degree", "diploma", "board of examination",
        "institute of technology", "grade card", "academic"
    )

    private val identityKeywords = listOf(
        "aadhaar", "aadhar", "pan card", "permanent account number", "passport",
        "republic of india", "government of india", "govt of india", "voter id",
        "election commission", "driving licence", "driving license", "unique identification",
        "income tax department", "father's name", "date of birth", "dob:"
    )

    private val financeKeywords = listOf(
        "receipt", "invoice", "bill", "payment", "bank statement",
        "transaction", "amount paid", "tax invoice", "account number", "account no",
        "debit", "credit", "upi id", "gstin", "paid via", "total amount",
        "subtotal", "payment summary", "salary statement", "passbook"
    )

    private val officeKeywords = listOf(
        "employee", "salary slip", "payslip", "offer letter", "appointment letter",
        "relieving letter", "experience certificate", "company", "designation",
        "human resources", "pf number", "uan", "payroll", "employment contract"
    )

    private val vehicleKeywords = listOf(
        "registration certificate", "vehicle", "chassis no", "engine no", "rto",
        "pollution under control", "puc", "motor insurance", "rc book",
        "registration mark", "cubic capacity", "seating capacity", "chassis number",
        "engine number", "fitness certificate"
    )

    private val personalKeywords = listOf(
        "medical report", "prescription", "vaccination certificate", "birth certificate",
        "marriage certificate", "utility bill", "electricity bill", "water bill",
        "gas connection", "blood report", "lab report", "hospital"
    )

    fun classify(
        ocrText: String,
        fileName: String,
        mimeType: String
    ): ClassificationResult {
        val lowerText = ocrText.lowercase()
        val lowerName = fileName.lowercase()

        // 1. Run Document Detection
        val detection = DocumentDetector.detect(ocrText, fileName, mimeType)

        if (!detection.isDocument) {
            return ClassificationResult(
                isDocument = false,
                documentType = "Photograph / Non-Document",
                category = "Other Documents",
                documentConfidence = detection.documentConfidence,
                categoryConfidence = 0.1f,
                ocrText = ocrText,
                detectionReason = detection.detectionReason
            )
        }

        // 2. Category Keyword Matching
        val collegeScore = countMatches(lowerText, lowerName, collegeKeywords)
        val identityScore = countMatches(lowerText, lowerName, identityKeywords)
        val financeScore = countMatches(lowerText, lowerName, financeKeywords)
        val officeScore = countMatches(lowerText, lowerName, officeKeywords)
        val vehicleScore = countMatches(lowerText, lowerName, vehicleKeywords)
        val personalScore = countMatches(lowerText, lowerName, personalKeywords)

        val scores = mapOf(
            "College" to collegeScore,
            "Identity" to identityScore,
            "Finance" to financeScore,
            "Office" to officeScore,
            "Vehicle" to vehicleScore,
            "Personal" to personalScore
        )

        val maxEntry = scores.maxByOrNull { it.value }
        val maxScore = maxEntry?.value ?: 0

        val categoryConfidence = (maxScore / 4.0f).coerceIn(0.2f, 1.0f)

        val selectedCategory: String
        val documentType: String

        if (maxScore >= 1) {
            selectedCategory = maxEntry!!.key
            documentType = determineDocumentType(selectedCategory, lowerText, lowerName)
        } else {
            selectedCategory = "Other Documents"
            documentType = determineGenericDocumentType(mimeType, lowerName)
        }

        return ClassificationResult(
            isDocument = true,
            documentType = documentType,
            category = selectedCategory,
            documentConfidence = detection.documentConfidence,
            categoryConfidence = if (selectedCategory == "Other Documents") 0.4f else categoryConfidence,
            ocrText = ocrText,
            detectionReason = detection.detectionReason
        )
    }

    private fun countMatches(text: String, fileName: String, keywords: List<String>): Int {
        var count = 0
        keywords.forEach { keyword ->
            if (text.contains(keyword)) {
                count += 2
            }
            if (fileName.contains(keyword)) {
                count += 3
            }
        }
        return count
    }

    private fun determineDocumentType(category: String, text: String, fileName: String): String {
        val combined = "$text $fileName"
        return when (category) {
            "College" -> when {
                combined.contains("hall ticket") || combined.contains("hallticket") || combined.contains("admit card") -> "Hall Ticket"
                combined.contains("marksheet") || combined.contains("grade card") || combined.contains("transcript") -> "Marksheet"
                combined.contains("degree") || combined.contains("diploma") -> "Degree Certificate"
                else -> "College Document"
            }
            "Identity" -> when {
                combined.contains("aadhaar") || combined.contains("aadhar") -> "Aadhaar Card"
                combined.contains("pan card") || combined.contains("permanent account") -> "PAN Card"
                combined.contains("passport") -> "Passport"
                combined.contains("voter") -> "Voter ID"
                combined.contains("driving") -> "Driving License"
                else -> "Government ID"
            }
            "Finance" -> when {
                combined.contains("receipt") -> "Payment Receipt"
                combined.contains("tax invoice") || combined.contains("invoice") -> "Tax Invoice"
                combined.contains("bank statement") || combined.contains("statement") -> "Bank Statement"
                else -> "Financial Document"
            }
            "Office" -> when {
                combined.contains("salary") || combined.contains("payslip") -> "Salary Slip"
                combined.contains("offer letter") -> "Offer Letter"
                combined.contains("experience") -> "Experience Certificate"
                else -> "Office Document"
            }
            "Vehicle" -> when {
                combined.contains("registration") || combined.contains("rc") -> "Vehicle RC"
                combined.contains("insurance") -> "Vehicle Insurance"
                combined.contains("pollution") || combined.contains("puc") -> "PUC Certificate"
                else -> "Vehicle Document"
            }
            "Personal" -> when {
                combined.contains("medical") || combined.contains("prescription") -> "Medical Report"
                combined.contains("bill") -> "Utility Bill"
                else -> "Personal Record"
            }
            else -> "Unclassified Document"
        }
    }

    private fun determineGenericDocumentType(mimeType: String, fileName: String): String {
        return when {
            mimeType.startsWith("image/") -> "Scanned Document"
            mimeType == "application/pdf" -> "PDF Document"
            fileName.endsWith(".doc") || fileName.endsWith(".docx") -> "Word Document"
            else -> "Unclassified Document"
        }
    }
}
