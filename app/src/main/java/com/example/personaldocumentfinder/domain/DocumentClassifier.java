package com.example.personaldocumentfinder.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class DocumentClassifier {

    private DocumentClassifier() {}

    private static final Pattern AADHAAR_NUMBER_REGEX = Pattern.compile("\\b(\\d{4}\\s\\d{4}\\s\\d{4}|[xX\\d]{4}\\s[xX\\d]{4}\\s\\d{4}|\\d{12})\\b");
    private static final Pattern PAN_REGEX = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");
    public static final Pattern INDIAN_VEHICLE_REG_REGEX = Pattern.compile("(?i)\\b(?:AN|AP|AR|AS|BR|CG|CH|DD|DL|DN|GA|GJ|HP|HR|JH|JK|KA|KL|LA|LD|MH|ML|MN|MP|MZ|NL|OD|OR|PB|PY|RJ|SK|TN|TR|TS|UK|UP|UT|WB)\\s*[-]?\\s*[0-9]{1,2}\\s*[-]?\\s*[A-Z]{1,3}\\s*[-]?\\s*[0-9]{4}\\b");
    private static final Pattern AMOUNT_REGEX = Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*[\\d,]+(?:\\.\\d{2})?|\\b(?:total|paid|amount)\\s*[:=]?\\s*[\\d,]+");

    public static Pattern getIndianVehicleRegRegex() {
        return INDIAN_VEHICLE_REG_REGEX;
    }

    public static ClassificationResult classify(
            String ocrText,
            String fileName,
            String mimeType
    ) {
        String safeOcr = ocrText != null ? ocrText : "";
        String safeName = fileName != null ? fileName : "";
        String safeMime = mimeType != null ? mimeType : "";

        String lowerText = safeOcr.toLowerCase().trim();
        String lowerName = safeName.toLowerCase().trim();
        String combined = lowerText + " " + lowerName;

        // 1. Stage 1: Document Detection
        DetectionResult detection = DocumentDetector.detect(safeOcr, safeName, safeMime);

        if (detection.getState() == DocumentDetectionState.NON_DOCUMENT) {
            return new ClassificationResult(
                    false,
                    "Photograph / Non-Document",
                    "Other Documents",
                    detection.getDocumentConfidence(),
                    0.05f,
                    0.05f,
                    safeOcr,
                    detection.getDetectionReason()
            );
        }

        // 2. Stage 2: Notebook / Schoolwork Check
        boolean isNotebook = DocumentDetector.isNotebookOrSchoolwork(lowerText, lowerName);

        // 3. Stage 3: Multi-Signal Evidence Extraction per Document Type
        EvidenceResult aadhaarEvidence = !isNotebook ? evaluateAadhaarEvidence(combined, safeOcr) : null;
        EvidenceResult panEvidence = !isNotebook ? evaluatePanEvidence(combined, safeOcr) : null;
        EvidenceResult dlEvidence = !isNotebook ? evaluateDrivingLicenseEvidence(combined) : null;
        EvidenceResult rcEvidence = !isNotebook ? evaluateVehicleRcEvidence(combined, safeOcr) : null;
        EvidenceResult pucEvidence = !isNotebook ? evaluatePucEvidence(combined, safeOcr) : null;
        EvidenceResult hallTicketEvidence = evaluateHallTicketEvidence(combined);
        EvidenceResult feeReceiptEvidence = !isNotebook ? evaluateFeeReceiptEvidence(combined) : null;
        EvidenceResult taxInvoiceEvidence = !isNotebook ? evaluateTaxInvoiceEvidence(combined) : null;
        EvidenceResult salarySlipEvidence = !isNotebook ? evaluateSalarySlipEvidence(combined) : null;
        EvidenceResult marksheetEvidence = evaluateMarksheetEvidence(combined, safeOcr);
        EvidenceResult scholarshipEvidence = evaluateScholarshipEvidence(combined);
        EvidenceResult certificateEvidence = evaluateCertificateEvidence(combined);
        EvidenceResult collegeDocEvidence = evaluateCollegeDocumentEvidence(combined);

        List<EvidenceResult> candidates = new ArrayList<>();
        if (aadhaarEvidence != null) candidates.add(aadhaarEvidence);
        if (panEvidence != null) candidates.add(panEvidence);
        if (dlEvidence != null) candidates.add(dlEvidence);
        if (rcEvidence != null) candidates.add(rcEvidence);
        if (pucEvidence != null) candidates.add(pucEvidence);
        if (hallTicketEvidence != null) candidates.add(hallTicketEvidence);
        if (feeReceiptEvidence != null) candidates.add(feeReceiptEvidence);
        if (taxInvoiceEvidence != null) candidates.add(taxInvoiceEvidence);
        if (salarySlipEvidence != null) candidates.add(salarySlipEvidence);
        if (marksheetEvidence != null) candidates.add(marksheetEvidence);
        if (scholarshipEvidence != null) candidates.add(scholarshipEvidence);
        if (certificateEvidence != null) candidates.add(certificateEvidence);
        if (collegeDocEvidence != null) candidates.add(collegeDocEvidence);

        // Sort candidates by score descending
        Collections.sort(candidates, new Comparator<EvidenceResult>() {
            @Override
            public int compare(EvidenceResult o1, EvidenceResult o2) {
                return Integer.compare(o2.score, o1.score);
            }
        });

        List<EvidenceResult> confidentCandidates = new ArrayList<>();
        Set<String> confidentCategoriesSet = new HashSet<>();
        for (EvidenceResult c : candidates) {
            if (c.isConfident) {
                confidentCandidates.add(c);
                confidentCategoriesSet.add(c.category);
            }
        }

        // Check for Conflicting Evidence across Categories
        boolean hasConflictingCategories = confidentCategoriesSet.size() >= 2 ||
                (candidates.size() >= 2 &&
                        !candidates.get(0).category.equals(candidates.get(1).category) &&
                        candidates.get(0).score > 0 && candidates.get(1).score > 0 &&
                        Math.abs(candidates.get(0).score - candidates.get(1).score) <= 1);

        if (hasConflictingCategories) {
            return new ClassificationResult(
                    true,
                    "Other Document",
                    "Other Documents",
                    detection.getDocumentConfidence(),
                    0.35f,
                    0.35f,
                    safeOcr,
                    "Conflicting multi-category signals detected: Review required"
            );
        }

        // Best specific candidate (HIGH CONFIDENCE TIER)
        if (!confidentCandidates.isEmpty()) {
            EvidenceResult bestCandidate = confidentCandidates.get(0);
            float docTypeConf = Math.min(0.98f, Math.max(0.70f, bestCandidate.confidence));
            float catConf = Math.min(0.98f, Math.max(0.70f, bestCandidate.confidence));

            return new ClassificationResult(
                    true,
                    bestCandidate.documentType,
                    bestCandidate.category,
                    detection.getDocumentConfidence(),
                    catConf,
                    docTypeConf,
                    safeOcr,
                    "Strong corroborated evidence: " + bestCandidate.reason
            );
        }

        // Medium Confidence Tier: Probable candidate for review
        for (EvidenceResult candidate : candidates) {
            if (candidate.score >= 2 && detection.isDocument()) {
                return new ClassificationResult(
                        true,
                        candidate.documentType,
                        candidate.category,
                        detection.getDocumentConfidence(),
                        0.55f,
                        0.55f,
                        safeOcr,
                        "Probable candidate: " + candidate.reason
                );
            }
        }

        // Stage 4: Generic document fallback (LOW CONFIDENCE TIER)
        String genericType = determineGenericDocumentType(safeMime, lowerName);

        return new ClassificationResult(
                detection.isDocument(),
                genericType,
                "Other Documents",
                detection.getDocumentConfidence(),
                0.30f,
                0.25f,
                safeOcr,
                "Generic / insufficient evidence for specific document classification"
        );
    }

    private static class EvidenceResult {
        final String documentType;
        final String category;
        final int score;
        final float confidence;
        final boolean isConfident;
        final String reason;

        EvidenceResult(String documentType, String category, int score, float confidence, boolean isConfident, String reason) {
            this.documentType = documentType;
            this.category = category;
            this.score = score;
            this.confidence = confidence;
            this.isConfident = isConfident;
            this.reason = reason;
        }
    }

    // --- Aadhaar Evidence ---
    private static EvidenceResult evaluateAadhaarEvidence(String combined, String rawOcr) {
        boolean hasPrimary = combined.contains("aadhaar") || combined.contains("aadhar");
        if (!hasPrimary) return null;

        // Negative check: Checklists, admission instruction sheets, DigiLocker login screen, promotional ads
        boolean isNonAadhaarContext = (combined.contains("checklist") ||
                combined.contains("documents required") ||
                combined.contains("certificates to be submitted") ||
                combined.contains("death certificate") ||
                combined.contains("scheme") ||
                combined.contains("hostel maintenance") ||
                combined.contains("aadhaar required") ||
                combined.contains("submit aadhaar") ||
                combined.contains("mandatory") ||
                (combined.contains("digilocker") && !combined.contains("mera aadhaar") && !AADHAAR_NUMBER_REGEX.matcher(rawOcr).find()));

        if (isNonAadhaarContext && !AADHAAR_NUMBER_REGEX.matcher(rawOcr).find()) {
            return null;
        }

        boolean hasAuthority = combined.contains("unique identification authority") ||
                combined.contains("uidai") ||
                combined.contains("government of india") ||
                combined.contains("govt of india") ||
                combined.contains("mera aadhaar");

        boolean hasNumber = AADHAAR_NUMBER_REGEX.matcher(rawOcr).find();

        int demographicCount = countKeywords(
                combined,
                Arrays.asList("date of birth", "dob", "gender", "male", "female", "address", "yob", "year of birth")
        );

        int score = 1;
        if (hasAuthority) score += 3;
        if (hasNumber) score += 3;
        if (demographicCount >= 2) score += 2; else if (demographicCount == 1) score += 1;

        boolean isConfident = (hasAuthority && (hasNumber || demographicCount >= 2)) ||
                (hasNumber && demographicCount >= 2) ||
                (hasAuthority && hasNumber && demographicCount >= 1);

        float confidence;
        if (isConfident && hasAuthority && hasNumber) {
            confidence = 0.96f;
        } else if (isConfident) {
            confidence = 0.88f;
        } else if (score >= 2) {
            confidence = 0.55f;
        } else {
            confidence = 0.35f;
        }

        return new EvidenceResult(
                "Aadhaar Card",
                "Identity",
                score,
                confidence,
                isConfident,
                "Aadhaar terminology with authority=" + hasAuthority + ", number=" + hasNumber + ", demographics=" + demographicCount
        );
    }

    // --- PAN Card Evidence ---
    private static EvidenceResult evaluatePanEvidence(String combined, String rawOcr) {
        boolean hasPanPattern = PAN_REGEX.matcher(rawOcr).find();
        boolean hasPanKeyword = combined.contains("pan card") || combined.contains("permanent account number");
        boolean hasIncomeTax = combined.contains("income tax") || combined.contains("incometax");

        if (!hasPanPattern && !hasPanKeyword) return null;

        int score = 1;
        if (hasPanPattern) score += 3;
        if (hasPanKeyword) score += 2;
        if (hasIncomeTax) score += 2;

        boolean isConfident = hasPanPattern || (hasPanKeyword && hasIncomeTax);

        return new EvidenceResult(
                "PAN Card",
                "Identity",
                score,
                isConfident ? 0.92f : 0.50f,
                isConfident,
                "PAN pattern=" + hasPanPattern + ", income tax=" + hasIncomeTax
        );
    }

    // --- Driving License Evidence ---
    private static EvidenceResult evaluateDrivingLicenseEvidence(String combined) {
        boolean hasDlKeyword = combined.contains("driving licence") || combined.contains("driving license");
        if (!hasDlKeyword) return null;

        boolean hasCorroboration = combined.contains("transport") || combined.contains("rto") ||
                combined.contains("licence no") || combined.contains("license no") ||
                combined.contains("valid till") || combined.contains("motor vehicle");

        boolean isConfident = hasCorroboration;

        return new EvidenceResult(
                "Driving License",
                "Identity",
                isConfident ? 5 : 1,
                isConfident ? 0.88f : 0.40f,
                isConfident,
                "Driving licence keyword with corroboration=" + hasCorroboration
        );
    }

    // --- Vehicle RC Evidence ---
    private static EvidenceResult evaluateVehicleRcEvidence(String combined, String rawOcr) {
        // Negative check: College/School registration forms or homework
        boolean isAcademicRegistration = (combined.contains("college") || combined.contains("university") ||
                combined.contains("school") || combined.contains("student") || combined.contains("semester") ||
                combined.contains("admission")) && !combined.contains("motor vehicle") && !combined.contains("chassis no");

        if (isAcademicRegistration) return null;

        boolean hasPrimaryRc = combined.contains("registration certificate") ||
                combined.contains("certificate of registration") ||
                combined.contains("form 23") ||
                combined.contains("form 24") ||
                combined.contains("rc book") ||
                combined.contains("vahan");

        boolean hasVehicleRegNum = INDIAN_VEHICLE_REG_REGEX.matcher(rawOcr).find();

        int techSpecsCount = countKeywords(
                combined,
                Arrays.asList("chassis no", "chassis number", "engine no", "engine number", "vehicle class",
                        "maker class", "cubic capacity", "seating capacity", "fuel", "rto", "transport department")
        );

        // Must have primary RC phrasing OR (valid Indian registration plate AND at least 2 technical specs)
        if (!hasPrimaryRc && !(hasVehicleRegNum && techSpecsCount >= 2)) {
            return null;
        }

        int score = 0;
        if (hasPrimaryRc) score += 3;
        if (hasVehicleRegNum) score += 3;
        score += Math.min(techSpecsCount, 3);

        boolean isConfident = (hasPrimaryRc && (hasVehicleRegNum || techSpecsCount >= 1)) ||
                (hasVehicleRegNum && techSpecsCount >= 2);

        return new EvidenceResult(
                "Vehicle RC",
                "Vehicle",
                score,
                isConfident ? 0.90f : 0.50f,
                isConfident,
                "RC primary=" + hasPrimaryRc + ", vehicleReg=" + hasVehicleRegNum + ", techSpecs=" + techSpecsCount
        );
    }

    // --- PUC Evidence ---
    private static EvidenceResult evaluatePucEvidence(String combined, String rawOcr) {
        // Explicit check against Pre-University College / Course
        boolean isPreUniversityCourse = (combined.contains("college") ||
                combined.contains("board") ||
                combined.contains("marks") ||
                combined.contains("student") ||
                combined.contains("university") ||
                combined.contains("pre-university") ||
                combined.contains("pre university") ||
                combined.contains("semester") ||
                combined.contains("class test"));

        boolean hasEmissionTerms = combined.contains("co%") ||
                combined.contains("carbon monoxide") ||
                combined.contains("smoke density") ||
                combined.contains("bharat stage") ||
                combined.contains("bs-iv") ||
                combined.contains("bs-vi") ||
                combined.contains("emission norms") ||
                combined.contains("k-value") ||
                combined.contains("tail pipe");

        if (isPreUniversityCourse && !hasEmissionTerms) {
            return null;
        }

        boolean hasPucPrimary = combined.contains("pollution under control") ||
                (combined.contains("puc") && (hasEmissionTerms || combined.contains("exhaust") || combined.contains("emission")));

        if (!hasPucPrimary) return null;

        // PUC MUST have emission-specific evidence
        if (!hasEmissionTerms) return null;

        boolean hasValidity = combined.contains("valid up to") || combined.contains("valid upto") ||
                combined.contains("validity") || combined.contains("date of test");

        boolean hasVehicleRegNum = INDIAN_VEHICLE_REG_REGEX.matcher(rawOcr).find();

        int score = 3;
        if (hasEmissionTerms) score += 3;
        if (hasValidity) score += 2;
        if (hasVehicleRegNum) score += 2;

        boolean isConfident = hasEmissionTerms && (hasValidity || hasVehicleRegNum || combined.contains("pollution under control"));

        return new EvidenceResult(
                "PUC Certificate",
                "Vehicle",
                score,
                isConfident ? 0.92f : 0.50f,
                isConfident,
                "PUC primary=" + hasPucPrimary + ", emission=" + hasEmissionTerms + ", validity=" + hasValidity
        );
    }

    // --- Hall Ticket Evidence ---
    private static EvidenceResult evaluateHallTicketEvidence(String combined) {
        boolean hasPrimary = combined.contains("hall ticket") ||
                combined.contains("hallticket") ||
                combined.contains("admit card");

        if (!hasPrimary) return null;

        int examContextCount = countKeywords(
                combined,
                Arrays.asList("examination", "semester", "seat no", "roll no", "enrollment", "prn",
                        "subject", "exam date", "centre code", "center code", "candidate name")
        );

        boolean hasInstitution = combined.contains("university") ||
                combined.contains("college") ||
                combined.contains("board") ||
                combined.contains("institute");

        int score = 2;
        if (examContextCount >= 2) score += 3; else if (examContextCount == 1) score += 1;
        if (hasInstitution) score += 2;

        boolean isConfident = examContextCount >= 1 || hasInstitution;

        return new EvidenceResult(
                "Hall Ticket",
                "College",
                score,
                isConfident ? 0.92f : 0.50f,
                isConfident,
                "Hall Ticket primary=" + hasPrimary + ", examContext=" + examContextCount + ", institution=" + hasInstitution
        );
    }

    // --- Fee Receipt Evidence ---
    private static EvidenceResult evaluateFeeReceiptEvidence(String combined) {
        // Negative check: Circulars, exam notifications, instructions, notice boards
        boolean isNoticeOrCircular = combined.contains("notice") ||
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
                combined.contains("instructions");

        boolean hasFee = combined.contains("fee") || combined.contains("fees") || combined.contains("tuition") || combined.contains("challan");
        boolean hasReceiptContext = combined.contains("receipt") || combined.contains("paid") || combined.contains("payment");

        if (!hasFee || !hasReceiptContext) return null;

        // Explicit transaction/receipt proof signals
        boolean hasTransactionProof = combined.contains("receipt no") ||
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
                combined.contains("payment receipt");

        // If it looks like a notice/circular and lacks real transaction proof, it is NOT a receipt!
        if (isNoticeOrCircular && !hasTransactionProof) {
            return null;
        }

        boolean hasAmount = AMOUNT_REGEX.matcher(combined).find();
        boolean hasAcademicContext = combined.contains("student") || combined.contains("college") ||
                combined.contains("university") || combined.contains("admission") || combined.contains("semester");

        int score = 1;
        if (hasTransactionProof) score += 4;
        if (hasAmount) score += 2;
        if (hasAcademicContext) score += 1;

        boolean isConfident = hasTransactionProof && (hasAmount || hasAcademicContext);

        return new EvidenceResult(
                "Fee Receipt",
                "Finance",
                score,
                isConfident ? 0.92f : 0.45f,
                isConfident,
                "Fee Receipt with transactionProof=" + hasTransactionProof + ", amount=" + hasAmount
        );
    }

    // --- Tax Invoice Evidence ---
    private static EvidenceResult evaluateTaxInvoiceEvidence(String combined) {
        boolean hasPrimary = combined.contains("tax invoice") || combined.contains("bill of supply");
        if (!hasPrimary) return null;

        boolean hasGstin = combined.contains("gstin") || combined.contains("gst");
        boolean hasAmount = AMOUNT_REGEX.matcher(combined).find() || combined.contains("subtotal") || combined.contains("total");
        boolean hasInvoiceNo = combined.contains("invoice no") || combined.contains("invoice date");

        int score = 3;
        if (hasGstin) score += 2;
        if (hasAmount) score += 2;
        if (hasInvoiceNo) score += 1;

        boolean isConfident = hasGstin || hasAmount || hasInvoiceNo;

        return new EvidenceResult(
                "Tax Invoice",
                "Finance",
                score,
                isConfident ? 0.90f : 0.50f,
                isConfident,
                "Tax invoice primary with gstin=" + hasGstin + ", amount=" + hasAmount
        );
    }

    // --- Salary Slip Evidence ---
    private static EvidenceResult evaluateSalarySlipEvidence(String combined) {
        boolean hasPrimary = combined.contains("salary slip") || combined.contains("payslip") || combined.contains("pay slip");
        if (!hasPrimary) return null;

        boolean hasCorroboration = combined.contains("basic pay") || combined.contains("gross salary") ||
                combined.contains("net pay") || combined.contains("provident fund") ||
                combined.contains("pf no") || combined.contains("uan") || combined.contains("deductions");

        boolean isConfident = hasCorroboration;

        return new EvidenceResult(
                "Salary Slip",
                "Office",
                isConfident ? 5 : 1,
                isConfident ? 0.90f : 0.40f,
                isConfident,
                "Salary slip primary with payroll fields=" + hasCorroboration
        );
    }

    // --- Marksheet Evidence ---
    private static EvidenceResult evaluateMarksheetEvidence(String combined, String rawOcr) {
        // Negative check 1: UI/UX mockups, prototypes, app design screens
        if (DocumentDetector.isUiMockup(combined, "")) return null;

        boolean hasPrimary = combined.contains("marksheet") || combined.contains("mark sheet") ||
                combined.contains("grade card") || combined.contains("statement of marks") ||
                combined.contains("academic transcript") || combined.contains("transcript of records");

        boolean hasExamResultContext = (combined.contains("semester") || combined.contains("examination")) &&
                (combined.contains("results") || combined.contains("marks") || combined.contains("cgpa") || combined.contains("sgpa"));

        if (!hasPrimary && !hasExamResultContext) return null;

        // Structural Academic Grading Signals
        int academicScoreSignals = countKeywords(
                combined,
                Arrays.asList("subject", "marks", "grade", "credits", "percentage", "cgpa", "sgpa",
                        "passed", "promoted", "total marks", "max marks", "marks obtained",
                        "theory", "practical", "internal assessment", "course code", "grand total")
        );

        boolean hasInstitution = combined.contains("university") ||
                combined.contains("board of secondary") ||
                combined.contains("institute") ||
                combined.contains("college") ||
                combined.contains("controller of examinations");

        boolean hasStudentId = combined.contains("roll no") ||
                combined.contains("seat no") ||
                combined.contains("prn") ||
                combined.contains("registration no") ||
                combined.contains("enrollment no") ||
                combined.contains("candidate name");

        int score = 0;
        if (hasPrimary) score += 3;
        if (hasExamResultContext) score += 2;
        score += Math.min(academicScoreSignals, 4);
        if (hasInstitution) score += 2;
        if (hasStudentId) score += 2;

        // Marksheet MUST have structured academic scoring signals (subjects/grades/cgpa/marks) + (institution or student ID)
        boolean isConfident = academicScoreSignals >= 2 && (hasInstitution || hasStudentId);

        return new EvidenceResult(
                "Marksheet",
                "College",
                score,
                isConfident ? 0.92f : 0.45f,
                isConfident,
                "Marksheet primary=" + hasPrimary + ", scores=" + academicScoreSignals + ", inst=" + hasInstitution + ", id=" + hasStudentId
        );
    }

    // --- Certificate Evidence ---
    private static EvidenceResult evaluateCertificateEvidence(String combined) {
        // Negative check: Vehicle registration / pollution certificates belong to Vehicle
        if (combined.contains("chassis no") || combined.contains("engine no") || combined.contains("pollution under control") || combined.contains("motor vehicle")) {
            return null;
        }

        boolean hasCertificatePhrasing = combined.contains("this is to certify") ||
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
                combined.contains("appreciation certificate");

        boolean hasGeneralCertificateWord = combined.contains("certificate") || combined.contains("certification");
        if (!hasCertificatePhrasing && !hasGeneralCertificateWord) return null;

        boolean hasRecipientClue = combined.contains("awarded to") ||
                combined.contains("presented to") ||
                combined.contains("certify that") ||
                combined.contains("son of") ||
                combined.contains("daughter of") ||
                combined.contains("successfully completed") ||
                combined.contains("student of") ||
                combined.contains("participant");

        boolean hasIssuingAuthority = combined.contains("principal") ||
                combined.contains("director") ||
                combined.contains("president") ||
                combined.contains("authorized signatory") ||
                combined.contains("coordinator") ||
                combined.contains("convenor") ||
                combined.contains("dean") ||
                combined.contains("head of department");

        boolean isAcademic = combined.contains("college") ||
                combined.contains("university") ||
                combined.contains("institute") ||
                combined.contains("school") ||
                combined.contains("degree") ||
                combined.contains("diploma") ||
                combined.contains("btech") ||
                combined.contains("bonafide") ||
                combined.contains("academic");

        int score = 0;
        if (hasCertificatePhrasing) score += 3; else score += 1;
        if (hasRecipientClue) score += 2;
        if (hasIssuingAuthority) score += 2;
        if (isAcademic) score += 2;

        boolean isConfident = (hasCertificatePhrasing && (hasRecipientClue || hasIssuingAuthority || isAcademic)) ||
                (hasGeneralCertificateWord && hasRecipientClue && hasIssuingAuthority);

        String targetCategory = isAcademic ? "College" : "Personal";

        return new EvidenceResult(
                "Certificate",
                targetCategory,
                score,
                isConfident ? 0.90f : 0.45f,
                isConfident,
                "Certificate phrasing=" + hasCertificatePhrasing + ", recipient=" + hasRecipientClue + ", authority=" + hasIssuingAuthority + ", academic=" + isAcademic
        );
    }

    // --- Scholarship Evidence ---
    private static EvidenceResult evaluateScholarshipEvidence(String combined) {
        boolean hasPrimary = combined.contains("scholarship") ||
                combined.contains("sanction order") ||
                combined.contains("financial assistance") ||
                combined.contains("mahadbt") ||
                combined.contains("national scholarship portal") ||
                combined.contains("post-matric scholarship") ||
                combined.contains("fellowship") ||
                combined.contains("freeship");

        if (!hasPrimary) return null;

        // Negative check: Just a checklist mentioning "scholarship" without actual scheme/sanction context
        boolean isJustChecklist = combined.contains("checklist") && !combined.contains("application id") && !combined.contains("sanctioned");

        int supportingSignals = countKeywords(
                combined,
                Arrays.asList("sanction", "application id", "disbursement", "scholarship scheme",
                        "academic year", "financial assistance", "student", "college", "department of higher education",
                        "social welfare", "directorate", "beneficiary", "tuition fee waiver")
        );

        int score = 3;
        score += Math.min(supportingSignals, 4);
        if (isJustChecklist) score -= 2;

        boolean isConfident = supportingSignals >= 2 && !isJustChecklist;

        return new EvidenceResult(
                "Scholarship Document",
                "College",
                score,
                isConfident ? 0.90f : 0.45f,
                isConfident,
                "Scholarship primary with supporting signals=" + supportingSignals
        );
    }

    // --- College Document / Notice Evidence (Circulars, Backlog notices, Class tests) ---
    private static EvidenceResult evaluateCollegeDocumentEvidence(String combined) {
        boolean hasNoticePhrases = combined.contains("students are hereby informed") ||
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
                combined.contains("learnathon");

        boolean hasCollegeContext = combined.contains("college") ||
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
                combined.contains("academic");

        if (!hasNoticePhrases && !hasCollegeContext) return null;

        int score = 1;
        if (hasNoticePhrases) score += 3;
        if (hasCollegeContext) score += 2;

        boolean isConfident = (hasNoticePhrases && hasCollegeContext) ||
                (hasNoticePhrases && (combined.contains("examination cell") || combined.contains("students are hereby informed") || combined.contains("circular")));
        boolean isNotice = combined.contains("notice") || combined.contains("circular") || combined.contains("informed") || combined.contains("cell");

        return new EvidenceResult(
                isNotice ? "College Notice" : "College Document",
                "College",
                score,
                isConfident ? 0.88f : 0.45f,
                isConfident,
                "Academic/College document evidence noticePhrases=" + hasNoticePhrases + ", collegeContext=" + hasCollegeContext
        );
    }

    private static int countKeywords(String text, List<String> keywords) {
        int count = 0;
        for (String kw : keywords) {
            if (text.contains(kw)) count++;
        }
        return count;
    }

    private static String determineGenericDocumentType(String mimeType, String fileName) {
        if (mimeType.equals("application/pdf") || fileName.endsWith(".pdf")) {
            return "PDF Document";
        } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
            return "Word Document";
        } else if (mimeType.startsWith("image/")) {
            return "Scanned Document";
        } else {
            return "Other Document";
        }
    }
}
