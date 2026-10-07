package com.example.personaldocumentfinder.domain

data class ClassificationResult(
    val isDocument: Boolean,
    val documentType: String,
    val category: String,
    val documentConfidence: Float,
    val categoryConfidence: Float,
    val documentTypeConfidence: Float = categoryConfidence,
    val ocrText: String = "",
    val detectionReason: String = ""
)

object DocumentClassifier {

    private val aadhaarNumberRegex = Regex("""\b(\d{4}\s\d{4}\s\d{4}|[xX\d]{4}\s[xX\d]{4}\s\d{4}|\d{12})\b""")
    private val panRegex = Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""")
    val indianVehicleRegRegex = Regex("""(?i)\b(?:AN|AP|AR|AS|BR|CG|CH|DD|DL|DN|GA|GJ|HP|HR|JH|JK|KA|KL|LA|LD|MH|ML|MN|MP|MZ|NL|OD|OR|PB|PY|RJ|SK|TN|TR|TS|UK|UP|UT|WB)\s*[-]?\s*[0-9]{1,2}\s*[-]?\s*[A-Z]{1,3}\s*[-]?\s*[0-9]{4}\b""")
    private val amountRegex = Regex("""(?i)(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{2})?|\b(?:total|paid|amount)\s*[:=]?\s*[\d,]+""")

    fun classify(
        ocrText: String,
        fileName: String,
        mimeType: String
    ): ClassificationResult {
        val lowerText = ocrText.lowercase().trim()
        val lowerName = fileName.lowercase().trim()
        val combined = "$lowerText $lowerName"

        // 1. Stage 1: Document Detection
        val detection = DocumentDetector.detect(ocrText, fileName, mimeType)

        if (detection.state == DocumentDetectionState.NON_DOCUMENT) {
            return ClassificationResult(
                isDocument = false,
                documentType = "Photograph / Non-Document",
                category = "Other Documents",
                documentConfidence = detection.documentConfidence,
                categoryConfidence = 0.05f,
                documentTypeConfidence = 0.05f,
                ocrText = ocrText,
                detectionReason = detection.detectionReason
            )
        }

        // 2. Stage 2: Notebook / Schoolwork Check
        val isNotebook = DocumentDetector.isNotebookOrSchoolwork(lowerText, lowerName)

        // 3. Stage 3: Multi-Signal Evidence Extraction per Document Type
        val aadhaarEvidence = if (!isNotebook) evaluateAadhaarEvidence(combined, ocrText) else null
        val panEvidence = if (!isNotebook) evaluatePanEvidence(combined, ocrText) else null
        val dlEvidence = if (!isNotebook) evaluateDrivingLicenseEvidence(combined) else null
        val rcEvidence = if (!isNotebook) evaluateVehicleRcEvidence(combined, ocrText) else null
        val pucEvidence = if (!isNotebook) evaluatePucEvidence(combined, ocrText) else null
        val hallTicketEvidence = evaluateHallTicketEvidence(combined)
        val feeReceiptEvidence = if (!isNotebook) evaluateFeeReceiptEvidence(combined) else null
        val taxInvoiceEvidence = if (!isNotebook) evaluateTaxInvoiceEvidence(combined) else null
        val salarySlipEvidence = if (!isNotebook) evaluateSalarySlipEvidence(combined) else null
        val marksheetEvidence = evaluateMarksheetEvidence(combined, ocrText)
        val scholarshipEvidence = evaluateScholarshipEvidence(combined)
        val certificateEvidence = evaluateCertificateEvidence(combined)
        val collegeDocEvidence = evaluateCollegeDocumentEvidence(combined)

        // Evaluate all candidates
        val candidates = listOfNotNull(
            aadhaarEvidence,
            panEvidence,
            dlEvidence,
            rcEvidence,
            pucEvidence,
            hallTicketEvidence,
            feeReceiptEvidence,
            taxInvoiceEvidence,
            salarySlipEvidence,
            marksheetEvidence,
            scholarshipEvidence,
            certificateEvidence,
            collegeDocEvidence
        )

        val sortedCandidates = candidates.sortedByDescending { it.score }
        val confidentCandidates = sortedCandidates.filter { it.isConfident }
        val confidentCategories = confidentCandidates.map { it.category }.distinct()

        // Check for Conflicting Evidence across Categories
        val hasConflictingCategories = confidentCategories.size >= 2 ||
                (sortedCandidates.size >= 2 &&
                        sortedCandidates[0].category != sortedCandidates[1].category &&
                        sortedCandidates[0].score > 0 && sortedCandidates[1].score > 0 &&
                        kotlin.math.abs(sortedCandidates[0].score - sortedCandidates[1].score) <= 1)

        if (hasConflictingCategories) {
            return ClassificationResult(
                isDocument = true,
                documentType = "Other Document",
                category = "Other Documents",
                documentConfidence = detection.documentConfidence,
                categoryConfidence = 0.35f,
                documentTypeConfidence = 0.35f,
                ocrText = ocrText,
                detectionReason = "Conflicting multi-category signals detected: Review required"
            )
        }

        // Best specific candidate (HIGH CONFIDENCE TIER)
        val bestCandidate = confidentCandidates.firstOrNull()

        if (bestCandidate != null && bestCandidate.isConfident) {
            val docTypeConf = bestCandidate.confidence.coerceIn(0.70f, 0.98f)
            val catConf = bestCandidate.confidence.coerceIn(0.70f, 0.98f)

            return ClassificationResult(
                isDocument = true,
                documentType = bestCandidate.documentType,
                category = bestCandidate.category,
                documentConfidence = detection.documentConfidence,
                categoryConfidence = catConf,
                documentTypeConfidence = docTypeConf,
                ocrText = ocrText,
                detectionReason = "Strong corroborated evidence: ${bestCandidate.reason}"
            )
        }

        // Medium Confidence Tier: Probable candidate for review
        val probableCandidate = sortedCandidates.firstOrNull { it.score >= 2 }
        if (probableCandidate != null && detection.isDocument) {
            return ClassificationResult(
                isDocument = true,
                documentType = probableCandidate.documentType,
                category = probableCandidate.category,
                documentConfidence = detection.documentConfidence,
                categoryConfidence = 0.55f,
                documentTypeConfidence = 0.55f,
                ocrText = ocrText,
                detectionReason = "Probable candidate: ${probableCandidate.reason}"
            )
        }

        // Stage 4: Generic document fallback (LOW CONFIDENCE TIER)
        val genericType = determineGenericDocumentType(mimeType, lowerName)

        return ClassificationResult(
            isDocument = detection.isDocument,
            documentType = genericType,
            category = "Other Documents",
            documentConfidence = detection.documentConfidence,
            categoryConfidence = 0.30f,
            documentTypeConfidence = 0.25f,
            ocrText = ocrText,
            detectionReason = "Generic / insufficient evidence for specific document classification"
        )
    }

    private data class EvidenceResult(
        val documentType: String,
        val category: String,
        val score: Int,
        val confidence: Float,
        val isConfident: Boolean,
        val reason: String
    )

    // --- Aadhaar Evidence ---
    private fun evaluateAadhaarEvidence(combined: String, rawOcr: String): EvidenceResult? {
        val hasPrimary = combined.contains("aadhaar") || combined.contains("aadhar")
        if (!hasPrimary) return null

        // Negative check: Checklists, admission instruction sheets, DigiLocker login screen, promotional ads
        val isNonAadhaarContext = (combined.contains("checklist") ||
                combined.contains("documents required") ||
                combined.contains("certificates to be submitted") ||
                combined.contains("death certificate") ||
                combined.contains("scheme") ||
                combined.contains("hostel maintenance") ||
                combined.contains("aadhaar required") ||
                combined.contains("submit aadhaar") ||
                combined.contains("mandatory") ||
                (combined.contains("digilocker") && !combined.contains("mera aadhaar") && !aadhaarNumberRegex.containsMatchIn(rawOcr)))

        if (isNonAadhaarContext && !aadhaarNumberRegex.containsMatchIn(rawOcr)) {
            return null
        }

        val hasAuthority = combined.contains("unique identification authority") ||
                combined.contains("uidai") ||
                combined.contains("government of india") ||
                combined.contains("govt of india") ||
                combined.contains("mera aadhaar")

        val hasNumber = aadhaarNumberRegex.containsMatchIn(rawOcr)

        val demographicCount = countKeywords(
            combined,
            listOf("date of birth", "dob", "gender", "male", "female", "address", "yob", "year of birth")
        )

        var score = 1
        if (hasAuthority) score += 3
        if (hasNumber) score += 3
        if (demographicCount >= 2) score += 2 else if (demographicCount == 1) score += 1

        val isConfident = (hasAuthority && (hasNumber || demographicCount >= 2)) ||
                (hasNumber && demographicCount >= 2) ||
                (hasAuthority && hasNumber && demographicCount >= 1)

        val confidence = when {
            isConfident && hasAuthority && hasNumber -> 0.96f
            isConfident -> 0.88f
            score >= 2 -> 0.55f
            else -> 0.35f
        }

        return EvidenceResult(
            documentType = "Aadhaar Card",
            category = "Identity",
            score = score,
            confidence = confidence,
            isConfident = isConfident,
            reason = "Aadhaar terminology with authority=$hasAuthority, number=$hasNumber, demographics=$demographicCount"
        )
    }

    // --- PAN Card Evidence ---
    private fun evaluatePanEvidence(combined: String, rawOcr: String): EvidenceResult? {
        val hasPanPattern = panRegex.containsMatchIn(rawOcr)
        val hasPanKeyword = combined.contains("pan card") || combined.contains("permanent account number")
        val hasIncomeTax = combined.contains("income tax") || combined.contains("incometax")

        if (!hasPanPattern && !hasPanKeyword) return null

        var score = 1
        if (hasPanPattern) score += 3
        if (hasPanKeyword) score += 2
        if (hasIncomeTax) score += 2

        val isConfident = hasPanPattern || (hasPanKeyword && hasIncomeTax)

        return EvidenceResult(
            documentType = "PAN Card",
            category = "Identity",
            score = score,
            confidence = if (isConfident) 0.92f else 0.50f,
            isConfident = isConfident,
            reason = "PAN pattern=$hasPanPattern, income tax=$hasIncomeTax"
        )
    }

    // --- Driving License Evidence ---
    private fun evaluateDrivingLicenseEvidence(combined: String): EvidenceResult? {
        val hasDlKeyword = combined.contains("driving licence") || combined.contains("driving license")
        if (!hasDlKeyword) return null

        val hasCorroboration = combined.contains("transport") || combined.contains("rto") ||
                combined.contains("licence no") || combined.contains("license no") ||
                combined.contains("valid till") || combined.contains("motor vehicle")

        val isConfident = hasCorroboration

        return EvidenceResult(
            documentType = "Driving License",
            category = "Identity",
            score = if (isConfident) 5 else 1,
            confidence = if (isConfident) 0.88f else 0.40f,
            isConfident = isConfident,
            reason = "Driving licence keyword with corroboration=$hasCorroboration"
        )
    }

    // --- Vehicle RC Evidence ---
    private fun evaluateVehicleRcEvidence(combined: String, rawOcr: String): EvidenceResult? {
        // Negative check: College/School registration forms or homework
        val isAcademicRegistration = (combined.contains("college") || combined.contains("university") ||
                combined.contains("school") || combined.contains("student") || combined.contains("semester") ||
                combined.contains("admission")) && !combined.contains("motor vehicle") && !combined.contains("chassis no")

        if (isAcademicRegistration) return null

        val hasPrimaryRc = combined.contains("registration certificate") ||
                combined.contains("certificate of registration") ||
                combined.contains("form 23") ||
                combined.contains("form 24") ||
                combined.contains("rc book") ||
                combined.contains("vahan")

        val hasVehicleRegNum = indianVehicleRegRegex.containsMatchIn(rawOcr)

        val techSpecsCount = countKeywords(
            combined,
            listOf("chassis no", "chassis number", "engine no", "engine number", "vehicle class",
                "maker class", "cubic capacity", "seating capacity", "fuel", "rto", "transport department")
        )

        // Must have primary RC phrasing OR (valid Indian registration plate AND at least 2 technical specs)
        if (!hasPrimaryRc && !(hasVehicleRegNum && techSpecsCount >= 2)) {
            return null
        }

        var score = 0
        if (hasPrimaryRc) score += 3
        if (hasVehicleRegNum) score += 3
        score += techSpecsCount.coerceAtMost(3)

        val isConfident = (hasPrimaryRc && (hasVehicleRegNum || techSpecsCount >= 1)) ||
                (hasVehicleRegNum && techSpecsCount >= 2)

        return EvidenceResult(
            documentType = "Vehicle RC",
            category = "Vehicle",
            score = score,
            confidence = if (isConfident) 0.90f else 0.50f,
            isConfident = isConfident,
            reason = "RC primary=$hasPrimaryRc, vehicleReg=$hasVehicleRegNum, techSpecs=$techSpecsCount"
        )
    }

    // --- PUC Evidence ---
    private fun evaluatePucEvidence(combined: String, rawOcr: String): EvidenceResult? {
        // Explicit check against Pre-University College / Course
        val isPreUniversityCourse = (combined.contains("college") ||
                combined.contains("board") ||
                combined.contains("marks") ||
                combined.contains("student") ||
                combined.contains("university") ||
                combined.contains("pre-university") ||
                combined.contains("pre university") ||
                combined.contains("semester") ||
                combined.contains("class test"))

        val hasEmissionTerms = combined.contains("co%") ||
                combined.contains("carbon monoxide") ||
                combined.contains("smoke density") ||
                combined.contains("bharat stage") ||
                combined.contains("bs-iv") ||
                combined.contains("bs-vi") ||
                combined.contains("emission norms") ||
                combined.contains("k-value") ||
                combined.contains("tail pipe")

        if (isPreUniversityCourse && !hasEmissionTerms) {
            return null
        }

        val hasPucPrimary = combined.contains("pollution under control") ||
                (combined.contains("puc") && (hasEmissionTerms || combined.contains("exhaust") || combined.contains("emission")))

        if (!hasPucPrimary) return null

        // PUC MUST have emission-specific evidence
        if (!hasEmissionTerms) return null

        val hasValidity = combined.contains("valid up to") || combined.contains("valid upto") ||
                combined.contains("validity") || combined.contains("date of test")

        val hasVehicleRegNum = indianVehicleRegRegex.containsMatchIn(rawOcr)

        var score = 3
        if (hasEmissionTerms) score += 3
        if (hasValidity) score += 2
        if (hasVehicleRegNum) score += 2

        val isConfident = hasEmissionTerms && (hasValidity || hasVehicleRegNum || combined.contains("pollution under control"))

        return EvidenceResult(
            documentType = "PUC Certificate",
            category = "Vehicle",
            score = score,
            confidence = if (isConfident) 0.92f else 0.50f,
            isConfident = isConfident,
            reason = "PUC primary=$hasPucPrimary, emission=$hasEmissionTerms, validity=$hasValidity"
        )
    }

    // --- Hall Ticket Evidence ---
    private fun evaluateHallTicketEvidence(combined: String): EvidenceResult? {
        val hasPrimary = combined.contains("hall ticket") ||
                combined.contains("hallticket") ||
                combined.contains("admit card")

        if (!hasPrimary) return null

        val examContextCount = countKeywords(
            combined,
            listOf("examination", "semester", "seat no", "roll no", "enrollment", "prn",
                "subject", "exam date", "centre code", "center code", "candidate name")
        )

        val hasInstitution = combined.contains("university") ||
                combined.contains("college") ||
                combined.contains("board") ||
                combined.contains("institute")

        var score = 2
        if (examContextCount >= 2) score += 3 else if (examContextCount == 1) score += 1
        if (hasInstitution) score += 2

        val isConfident = examContextCount >= 1 || hasInstitution

        return EvidenceResult(
            documentType = "Hall Ticket",
            category = "College",
            score = score,
            confidence = if (isConfident) 0.92f else 0.50f,
            isConfident = isConfident,
            reason = "Hall Ticket primary=$hasPrimary, examContext=$examContextCount, institution=$hasInstitution"
        )
    }

    // --- Fee Receipt Evidence ---
    private fun evaluateFeeReceiptEvidence(combined: String): EvidenceResult? {
        // Negative check: Circulars, exam notifications, instructions, notice boards
        val isNoticeOrCircular = combined.contains("notice") ||
                combined.contains("notification") ||
                combined.contains("circular") ||
                combined.contains("examination cell") ||
                combined.contains("exam cell") ||
                combined.contains("hereby informed") ||
                combined.contains("students are informed") ||
                combined.contains("schedule") ||
                combined.contains("copy to") ||
                combined.contains("registrar") ||
                combined.contains("dean") ||
                combined.contains("director") ||
                combined.contains("instructions")

        val hasFee = combined.contains("fee") || combined.contains("fees") || combined.contains("tuition") || combined.contains("challan")
        val hasReceiptContext = combined.contains("receipt") || combined.contains("paid") || combined.contains("payment")

        if (!hasFee || !hasReceiptContext) return null

        // Explicit transaction/receipt proof signals
        val hasTransactionProof = combined.contains("receipt no") ||
                combined.contains("receipt number") ||
                combined.contains("receipt #") ||
                combined.contains("txn id") ||
                combined.contains("transaction id") ||
                combined.contains("ref no") ||
                combined.contains("reference no") ||
                combined.contains("payment successful") ||
                combined.contains("received with thanks") ||
                combined.contains("received from") ||
                combined.contains("amount paid") ||
                combined.contains("total amount paid") ||
                combined.contains("payment receipt")

        // If it looks like a notice/circular and lacks real transaction proof, it is NOT a receipt!
        if (isNoticeOrCircular && !hasTransactionProof) {
            return null
        }

        val hasAmount = amountRegex.containsMatchIn(combined)
        val hasAcademicContext = combined.contains("student") || combined.contains("college") ||
                combined.contains("university") || combined.contains("admission") || combined.contains("semester")

        var score = 1
        if (hasTransactionProof) score += 4
        if (hasAmount) score += 2
        if (hasAcademicContext) score += 1

        val isConfident = hasTransactionProof && (hasAmount || hasAcademicContext)

        return EvidenceResult(
            documentType = "Fee Receipt",
            category = "Finance",
            score = score,
            confidence = if (isConfident) 0.92f else 0.45f,
            isConfident = isConfident,
            reason = "Fee Receipt with transactionProof=$hasTransactionProof, amount=$hasAmount"
        )
    }

    // --- Tax Invoice Evidence ---
    private fun evaluateTaxInvoiceEvidence(combined: String): EvidenceResult? {
        val hasPrimary = combined.contains("tax invoice") || combined.contains("bill of supply")
        if (!hasPrimary) return null

        val hasGstin = combined.contains("gstin") || combined.contains("gst")
        val hasAmount = amountRegex.containsMatchIn(combined) || combined.contains("subtotal") || combined.contains("total")
        val hasInvoiceNo = combined.contains("invoice no") || combined.contains("invoice date")

        var score = 3
        if (hasGstin) score += 2
        if (hasAmount) score += 2
        if (hasInvoiceNo) score += 1

        val isConfident = hasGstin || hasAmount || hasInvoiceNo

        return EvidenceResult(
            documentType = "Tax Invoice",
            category = "Finance",
            score = score,
            confidence = if (isConfident) 0.90f else 0.50f,
            isConfident = isConfident,
            reason = "Tax invoice primary with gstin=$hasGstin, amount=$hasAmount"
        )
    }

    // --- Salary Slip Evidence ---
    private fun evaluateSalarySlipEvidence(combined: String): EvidenceResult? {
        val hasPrimary = combined.contains("salary slip") || combined.contains("payslip") || combined.contains("pay slip")
        if (!hasPrimary) return null

        val hasCorroboration = combined.contains("basic pay") || combined.contains("gross salary") ||
                combined.contains("net pay") || combined.contains("provident fund") ||
                combined.contains("pf no") || combined.contains("uan") || combined.contains("deductions")

        val isConfident = hasCorroboration

        return EvidenceResult(
            documentType = "Salary Slip",
            category = "Office",
            score = if (isConfident) 5 else 1,
            confidence = if (isConfident) 0.90f else 0.40f,
            isConfident = isConfident,
            reason = "Salary slip primary with payroll fields=$hasCorroboration"
        )
    }

    // --- Marksheet Evidence ---
    private fun evaluateMarksheetEvidence(combined: String, rawOcr: String): EvidenceResult? {
        // Negative check 1: UI/UX mockups, prototypes, app design screens
        if (DocumentDetector.isUiMockup(combined, "")) return null

        val hasPrimary = combined.contains("marksheet") || combined.contains("mark sheet") ||
                combined.contains("grade card") || combined.contains("statement of marks") ||
                combined.contains("academic transcript") || combined.contains("transcript of records")

        val hasExamResultContext = (combined.contains("semester") || combined.contains("examination")) &&
                (combined.contains("results") || combined.contains("marks") || combined.contains("cgpa") || combined.contains("sgpa"))

        if (!hasPrimary && !hasExamResultContext) return null

        // Structural Academic Grading Signals
        val academicScoreSignals = countKeywords(
            combined,
            listOf("subject", "marks", "grade", "credits", "percentage", "cgpa", "sgpa",
                "passed", "promoted", "total marks", "max marks", "marks obtained",
                "theory", "practical", "internal assessment", "course code", "grand total")
        )

        val hasInstitution = combined.contains("university") ||
                combined.contains("board of secondary") ||
                combined.contains("institute") ||
                combined.contains("college") ||
                combined.contains("controller of examinations")

        val hasStudentId = combined.contains("roll no") ||
                combined.contains("seat no") ||
                combined.contains("prn") ||
                combined.contains("registration no") ||
                combined.contains("enrollment no") ||
                combined.contains("candidate name")

        var score = 0
        if (hasPrimary) score += 3
        if (hasExamResultContext) score += 2
        score += academicScoreSignals.coerceAtMost(4)
        if (hasInstitution) score += 2
        if (hasStudentId) score += 2

        // Marksheet MUST have structured academic scoring signals (subjects/grades/cgpa/marks) + (institution or student ID)
        val isConfident = academicScoreSignals >= 2 && (hasInstitution || hasStudentId)

        return EvidenceResult(
            documentType = "Marksheet",
            category = "College",
            score = score,
            confidence = if (isConfident) 0.92f else 0.45f,
            isConfident = isConfident,
            reason = "Marksheet primary=$hasPrimary, scores=$academicScoreSignals, inst=$hasInstitution, id=$hasStudentId"
        )
    }

    // --- Certificate Evidence ---
    private fun evaluateCertificateEvidence(combined: String): EvidenceResult? {
        // Negative check: Vehicle registration / pollution certificates belong to Vehicle
        if (combined.contains("chassis no") || combined.contains("engine no") || combined.contains("pollution under control") || combined.contains("motor vehicle")) {
            return null
        }

        val hasCertificatePhrasing = combined.contains("this is to certify") ||
                combined.contains("certifies that") ||
                combined.contains("is hereby awarded") ||
                combined.contains("awarded to") ||
                combined.contains("presented to") ||
                combined.contains("in recognition of") ||
                combined.contains("certificate of completion") ||
                combined.contains("certificate of achievement") ||
                combined.contains("certificate of participation") ||
                combined.contains("certificate of merit") ||
                combined.contains("course completion certificate") ||
                combined.contains("degree certificate") ||
                combined.contains("bonafide certificate") ||
                combined.contains("passing certificate") ||
                combined.contains("provisional certificate") ||
                combined.contains("transfer certificate") ||
                combined.contains("appreciation certificate")

        val hasGeneralCertificateWord = combined.contains("certificate") || combined.contains("certification")
        if (!hasCertificatePhrasing && !hasGeneralCertificateWord) return null

        val hasRecipientClue = combined.contains("awarded to") ||
                combined.contains("presented to") ||
                combined.contains("certify that") ||
                combined.contains("son of") ||
                combined.contains("daughter of") ||
                combined.contains("successfully completed") ||
                combined.contains("student of") ||
                combined.contains("participant")

        val hasIssuingAuthority = combined.contains("principal") ||
                combined.contains("director") ||
                combined.contains("president") ||
                combined.contains("authorized signatory") ||
                combined.contains("coordinator") ||
                combined.contains("convenor") ||
                combined.contains("dean") ||
                combined.contains("head of department")

        val isAcademic = combined.contains("college") ||
                combined.contains("university") ||
                combined.contains("institute") ||
                combined.contains("school") ||
                combined.contains("degree") ||
                combined.contains("diploma") ||
                combined.contains("btech") ||
                combined.contains("bonafide") ||
                combined.contains("academic")

        var score = 0
        if (hasCertificatePhrasing) score += 3 else score += 1
        if (hasRecipientClue) score += 2
        if (hasIssuingAuthority) score += 2
        if (isAcademic) score += 2

        val isConfident = (hasCertificatePhrasing && (hasRecipientClue || hasIssuingAuthority || isAcademic)) ||
                (hasGeneralCertificateWord && hasRecipientClue && hasIssuingAuthority)

        val targetCategory = if (isAcademic) "College" else "Personal"

        return EvidenceResult(
            documentType = "Certificate",
            category = targetCategory,
            score = score,
            confidence = if (isConfident) 0.90f else 0.45f,
            isConfident = isConfident,
            reason = "Certificate phrasing=$hasCertificatePhrasing, recipient=$hasRecipientClue, authority=$hasIssuingAuthority, academic=$isAcademic"
        )
    }

    // --- Scholarship Evidence ---
    private fun evaluateScholarshipEvidence(combined: String): EvidenceResult? {
        val hasPrimary = combined.contains("scholarship") ||
                combined.contains("sanction order") ||
                combined.contains("financial assistance") ||
                combined.contains("mahadbt") ||
                combined.contains("national scholarship portal") ||
                combined.contains("post-matric scholarship") ||
                combined.contains("fellowship") ||
                combined.contains("freeship")

        if (!hasPrimary) return null

        // Negative check: Just a checklist mentioning "scholarship" without actual scheme/sanction context
        val isJustChecklist = combined.contains("checklist") && !combined.contains("application id") && !combined.contains("sanctioned")

        val supportingSignals = countKeywords(
            combined,
            listOf("sanction", "application id", "disbursement", "scholarship scheme",
                "academic year", "financial assistance", "student", "college", "department of higher education",
                "social welfare", "directorate", "beneficiary", "tuition fee waiver")
        )

        var score = 3
        score += supportingSignals.coerceAtMost(4)
        if (isJustChecklist) score -= 2

        val isConfident = supportingSignals >= 2 && !isJustChecklist

        return EvidenceResult(
            documentType = "Scholarship Document",
            category = "College",
            score = score,
            confidence = if (isConfident) 0.90f else 0.45f,
            isConfident = isConfident,
            reason = "Scholarship primary with supporting signals=$supportingSignals"
        )
    }

    // --- College Document / Notice Evidence (Circulars, Backlog notices, Class tests) ---
    private fun evaluateCollegeDocumentEvidence(combined: String): EvidenceResult? {
        val hasNoticePhrases = combined.contains("students are hereby informed") ||
                combined.contains("hereby informed") ||
                combined.contains("circular") ||
                combined.contains("examination cell") ||
                combined.contains("exam cell") ||
                combined.contains("backlog") ||
                combined.contains("class test") ||
                combined.contains("internal assessment") ||
                combined.contains("seating arrangement") ||
                combined.contains("attendance") ||
                combined.contains("student portfolio") ||
                combined.contains("learnathon")

        val hasCollegeContext = combined.contains("college") ||
                combined.contains("university") ||
                combined.contains("institute") ||
                combined.contains("department") ||
                combined.contains("csd") ||
                combined.contains("btech") ||
                combined.contains("pec") ||
                combined.contains("dean") ||
                combined.contains("registrar") ||
                combined.contains("director") ||
                combined.contains("semester") ||
                combined.contains("student") ||
                combined.contains("examination") ||
                combined.contains("academic")

        if (!hasNoticePhrases && !hasCollegeContext) return null

        var score = 1
        if (hasNoticePhrases) score += 3
        if (hasCollegeContext) score += 2

        val isConfident = (hasNoticePhrases && hasCollegeContext) ||
                (hasNoticePhrases && (combined.contains("examination cell") || combined.contains("students are hereby informed") || combined.contains("circular")))
        val isNotice = combined.contains("notice") || combined.contains("circular") || combined.contains("informed") || combined.contains("cell")

        return EvidenceResult(
            documentType = if (isNotice) "College Notice" else "College Document",
            category = "College",
            score = score,
            confidence = if (isConfident) 0.88f else 0.45f,
            isConfident = isConfident,
            reason = "Academic/College document evidence noticePhrases=$hasNoticePhrases, collegeContext=$hasCollegeContext"
        )
    }

    private fun countKeywords(text: String, keywords: List<String>): Int {
        var count = 0
        keywords.forEach { kw ->
            if (text.contains(kw)) count++
        }
        return count
    }

    private fun determineGenericDocumentType(mimeType: String, fileName: String): String {
        return when {
            mimeType == "application/pdf" || fileName.endsWith(".pdf") -> "PDF Document"
            fileName.endsWith(".doc") || fileName.endsWith(".docx") -> "Word Document"
            mimeType.startsWith("image/") -> "Scanned Document"
            else -> "Other Document"
        }
    }
}
