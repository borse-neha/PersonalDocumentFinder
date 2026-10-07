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

    // Strong multi-word authoritative official document phrases
    private val strongOfficialPhrases = listOf(
        "government of india", "govt of india", "unique identification authority",
        "income tax department", "permanent account number", "hall ticket",
        "admit card", "examination board", "board of secondary education",
        "central board of secondary", "controller of examinations", "statement of marks",
        "tax invoice", "bill of supply", "bank statement", "statement of account",
        "registration certificate", "certificate of registration", "chassis no",
        "engine no", "salary slip", "offer letter", "appointment letter",
        "pollution under control", "terms and conditions", "application form",
        "grade card", "bonafide certificate", "all india council", "national health authority",
        "this is to certify", "certifies that", "certificate of completion",
        "certificate of achievement", "certificate of participation", "certificate of merit",
        "degree certificate", "provisional certificate", "passing certificate",
        "transfer certificate", "scholarship scheme", "scholarship sanction",
        "scholarship application", "sanction order", "financial assistance",
        "mahadbt", "national scholarship portal", "examination cell",
        "students are hereby informed", "hereby informed", "office circular"
    )

    // Negative indicators: casual photos, social media, personal life events, food, landscapes
    private val nonDocumentKeywords = listOf(
        "selfie", "birthday", "wedding", "anniversary", "vacation", "holiday",
        "trip", "tour", "beach", "mountain", "landscape", "sunset", "sunrise",
        "scenery", "food", "recipe", "dish", "cooking", "snack", "dinner",
        "lunch", "breakfast", "cafe", "restaurant", "menu", "cocktail",
        "meme", "funny", "chat", "whatsapp chat", "camera", "portrait",
        "party", "celebration", "poster", "billboard", "signboard", "hotel"
    )

    // Negative indicators for UI/UX mockups, prototypes, app wireframes
    val uiMockupKeywords = listOf(
        "ui/ux", "ui ux", "wireframe", "mockup", "prototype", "figma",
        "splash screen", "home screen", "app screen", "empty state",
        "design screen", "screen design", "bottom navigation", "tab bar",
        "search bar", "user interface", "adobe xd"
    )

    // Negative indicators for notebook, handwritten math, school homework, exercises
    val notebookSchoolworkKeywords = listOf(
        "exercise", "solution", "homework", "classwork", "assignment", "chapter",
        "problem", "theorem", "formula", "derivative", "integral", "matrix",
        "determinant", "algebra", "calculus", "geometry", "rough work", "q.no",
        "ans:", "ex:", "proof:", "physics", "chemistry", "mathematics", "maths"
    )

    private val mathEquationRegexes = listOf(
        Regex("""(?i)\b[a-z]\s*=\s*[-+0-9]"""),
        Regex("""(?i)\b(?:sin|cos|tan|log|lim|dx|dy|dt)\b"""),
        Regex("""\b\d+\s*[-+x/]\s*\d+\s*="""),
        Regex("""(?i)\bpage\s*no\b""")
    )

    // Common camera photo filename prefixes
    private val cameraPrefixes = listOf(
        "img_", "dcim_", "pxl_", "photo_", "pic_", "dsc_", "camera_"
    )

    // Structural document key-value patterns
    private val structuralFieldRegexes = listOf(
        Regex("""(?i)\b(?:name|candidate|student|applicant)\s*[:=]"""),
        Regex("""(?i)\b(?:date|dob|date of birth)\s*[:=]"""),
        Regex("""(?i)\b(?:address|permanent address)\s*[:=]"""),
        Regex("""(?i)\b(?:roll no|seat no|registration no|reg no|regn no)\s*[:=]"""),
        Regex("""(?i)\b(?:amount|total amount|amount paid|subtotal|grand total|fee)\s*[:=]"""),
        Regex("""(?i)\b(?:chassis no|engine no|vehicle no)\s*[:=]"""),
        Regex("""(?i)\b(?:receipt no|invoice no|txn id|transaction id)\s*[:=]"""),
        Regex("""(?i)\b(?:valid upto|valid up to|validity|issue date)\s*[:=]"""),
        Regex("""(?i)\b(?:signature|authorized signatory)\b""")
    )

    private val documentNumberRegexes = listOf(
        Regex("""\b\d{4}\s\d{4}\s\d{4}\b"""), // Aadhaar format
        Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b"""), // PAN format
        Regex("""(?i)\b(?:AN|AP|AR|AS|BR|CG|CH|DD|DL|DN|GA|GJ|HP|HR|JH|JK|KA|KL|LA|LD|MH|ML|MN|MP|MZ|NL|OD|OR|PB|PY|RJ|SK|TN|TR|TS|UK|UP|UT|WB)\s*[-]?\s*[0-9]{1,2}\s*[-]?\s*[A-Z]{1,3}\s*[-]?\s*[0-9]{4}\b""") // Indian vehicle reg
    )

    private val dateRegex = Regex("""(?i)\b(?:\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{1,2}(?:st|nd|rd|th)?\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*)\b""")

    fun isUiMockup(lowerText: String, lowerName: String): Boolean {
        return uiMockupKeywords.any { kw ->
            lowerName.contains(kw) || lowerText.contains(Regex("""\b${Regex.escape(kw)}\b"""))
        }
    }

    fun isNotebookOrSchoolwork(lowerText: String, lowerName: String): Boolean {
        val hasNotebookKeyword = notebookSchoolworkKeywords.count { kw ->
            lowerName.contains(kw) || lowerText.contains(Regex("""\b$kw\b"""))
        }
        val hasMathEquation = mathEquationRegexes.count { it.containsMatchIn(lowerText) }
        val hasOfficialPhrase = strongOfficialPhrases.any { lowerText.contains(it) }

        return !hasOfficialPhrase && (hasNotebookKeyword >= 2 || (hasNotebookKeyword >= 1 && hasMathEquation >= 1) || hasMathEquation >= 2)
    }

    fun detect(
        ocrText: String,
        fileName: String,
        mimeType: String
    ): DetectionResult {
        val lowerText = ocrText.lowercase().trim()
        val lowerName = fileName.lowercase().trim()

        // 1. Explicit rejection for UI/UX mockups, prototypes, app screens
        if (isUiMockup(lowerText, lowerName)) {
            return DetectionResult(
                state = DocumentDetectionState.NON_DOCUMENT,
                documentConfidence = 0.05f,
                detectionReason = "UI/UX mockup / Prototype design screen detected (Rejected)"
            )
        }

        val hasStrongOfficialPhrase = strongOfficialPhrases.any { phrase -> lowerText.contains(phrase) }

        // 2. Explicit protection against notebook / handwritten homework pages
        if (isNotebookOrSchoolwork(lowerText, lowerName) && !hasStrongOfficialPhrase) {
            val isPdf = mimeType == "application/pdf" || lowerName.endsWith(".pdf")
            return DetectionResult(
                state = if (isPdf) DocumentDetectionState.UNCERTAIN else DocumentDetectionState.NON_DOCUMENT,
                documentConfidence = if (isPdf) 0.35f else 0.05f,
                detectionReason = "Notebook / Handwritten schoolwork / Calculation notes detected"
            )
        }

        // 3. PDF / Office Docs are intrinsically documents
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

        // 4. Reject obvious non-documents: casual photos, vacations, food, memes
        val hasNonDocKeyword = nonDocumentKeywords.any { kw ->
            lowerName.contains(kw) || lowerText.contains(Regex("""\b$kw\b"""))
        }

        if (hasNonDocKeyword && !hasStrongOfficialPhrase) {
            return DetectionResult(
                state = DocumentDetectionState.NON_DOCUMENT,
                documentConfidence = 0.05f,
                detectionReason = "Casual photo / Non-document subject detected (Rejected)"
            )
        }

        // 5. OCR Text Token Density
        val tokens = lowerText.split(Regex("""\s+""")).filter { it.length >= 2 }
        val wordCount = tokens.size

        // Photos with trivial text (< 3 words) are non-documents
        if (wordCount < 3) {
            return DetectionResult(
                state = DocumentDetectionState.NON_DOCUMENT,
                documentConfidence = 0.05f,
                detectionReason = "Photograph / Low text density (< 3 words)"
            )
        }

        val isCameraPhoto = cameraPrefixes.any { lowerName.startsWith(it) }

        // 6. Strong multi-word official document phrases
        if (hasStrongOfficialPhrase) {
            return DetectionResult(
                state = DocumentDetectionState.DOCUMENT,
                documentConfidence = 0.92f,
                detectionReason = "Strong official document keyphrase detected in content"
            )
        }

        // 7. Structural field matching
        val matchedStructuralFields = structuralFieldRegexes.count { it.containsMatchIn(lowerText) }
        val hasDocumentNumber = documentNumberRegexes.any { it.containsMatchIn(ocrText) }

        if (matchedStructuralFields >= 2 || (matchedStructuralFields >= 1 && hasDocumentNumber)) {
            return DetectionResult(
                state = DocumentDetectionState.DOCUMENT,
                documentConfidence = 0.88f,
                detectionReason = "Document structure and key-value fields detected"
            )
        }

        // 8. Camera photos with incidental text (signboards, t-shirts, posters, casual text)
        if (isCameraPhoto) {
            if (wordCount < 15 && matchedStructuralFields == 0 && !hasDocumentNumber) {
                return DetectionResult(
                    state = DocumentDetectionState.NON_DOCUMENT,
                    documentConfidence = 0.10f,
                    detectionReason = "Casual photograph with incidental text (Rejected)"
                )
            }
        }

        // 9. High text density with structured layout (wordCount >= 20 and date/number pattern)
        val hasDatePattern = dateRegex.containsMatchIn(lowerText)
        if (wordCount >= 20 && (hasDatePattern || matchedStructuralFields >= 1)) {
            return DetectionResult(
                state = DocumentDetectionState.DOCUMENT,
                documentConfidence = 0.75f,
                detectionReason = "Structured text & document fields detected"
            )
        }

        // 10. Screenshot handling
        val isScreenshot = lowerName.contains("screenshot") || lowerName.contains("screen_shot")
        if (isScreenshot) {
            val isChatApp = lowerText.contains("online") || lowerText.contains("typing...") ||
                    lowerText.contains("message") || lowerText.contains("forwarded")
            if (isChatApp) {
                return DetectionResult(
                    state = DocumentDetectionState.NON_DOCUMENT,
                    documentConfidence = 0.05f,
                    detectionReason = "Chat / Messaging screenshot (Rejected)"
                )
            }
            if (wordCount >= 8 && (matchedStructuralFields >= 1 || hasDatePattern || hasDocumentNumber)) {
                return DetectionResult(
                    state = DocumentDetectionState.DOCUMENT,
                    documentConfidence = 0.75f,
                    detectionReason = "Document content screenshot detected"
                )
            }
        }

        // 11. Borderline / Uncertain Candidates
        if (wordCount >= 10 && (matchedStructuralFields >= 1 || hasDatePattern)) {
            return DetectionResult(
                state = DocumentDetectionState.UNCERTAIN,
                documentConfidence = 0.45f,
                detectionReason = "Ambiguous document signals (Candidate Review Required)"
            )
        }

        // Default: Casual image with incidental text
        return DetectionResult(
            state = DocumentDetectionState.NON_DOCUMENT,
            documentConfidence = 0.10f,
            detectionReason = "Non-document photograph / Casual image (Rejected)"
        )
    }
}
