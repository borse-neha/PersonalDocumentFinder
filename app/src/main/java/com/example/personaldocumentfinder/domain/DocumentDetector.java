package com.example.personaldocumentfinder.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class DocumentDetector {

    private DocumentDetector() {}

    // Strong multi-word authoritative official document phrases
    private static final List<String> STRONG_OFFICIAL_PHRASES = Collections.unmodifiableList(Arrays.asList(
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
    ));

    // Negative indicators: casual photos, social media, personal life events, food, landscapes
    private static final List<String> NON_DOCUMENT_KEYWORDS = Collections.unmodifiableList(Arrays.asList(
            "selfie", "birthday", "wedding", "anniversary", "vacation", "holiday",
            "trip", "tour", "beach", "mountain", "landscape", "sunset", "sunrise",
            "scenery", "food", "recipe", "dish", "cooking", "snack", "dinner",
            "lunch", "breakfast", "cafe", "restaurant", "menu", "cocktail",
            "meme", "funny", "chat", "whatsapp chat", "camera", "portrait",
            "party", "celebration", "poster", "billboard", "signboard", "hotel"
    ));

    // Negative indicators for UI/UX mockups, prototypes, app wireframes
    public static final List<String> UI_MOCKUP_KEYWORDS = Collections.unmodifiableList(Arrays.asList(
            "ui/ux", "ui ux", "wireframe", "mockup", "prototype", "figma",
            "splash screen", "home screen", "app screen", "empty state",
            "design screen", "screen design", "bottom navigation", "tab bar",
            "search bar", "user interface", "adobe xd"
    ));

    // Negative indicators for notebook, handwritten math, school homework, exercises
    public static final List<String> NOTEBOOK_SCHOOLWORK_KEYWORDS = Collections.unmodifiableList(Arrays.asList(
            "exercise", "solution", "homework", "classwork", "assignment", "chapter",
            "problem", "theorem", "formula", "derivative", "integral", "matrix",
            "determinant", "algebra", "calculus", "geometry", "rough work", "q.no",
            "ans:", "ex:", "proof:", "physics", "chemistry", "mathematics", "maths"
    ));

    private static final List<Pattern> MATH_EQUATION_PATTERNS = Collections.unmodifiableList(Arrays.asList(
            Pattern.compile("(?i)\\b[a-z]\\s*=\\s*[-+0-9]"),
            Pattern.compile("(?i)\\b(?:sin|cos|tan|log|lim|dx|dy|dt)\\b"),
            Pattern.compile("\\b\\d+\\s*[-+x/]\\s*\\d+\\s*="),
            Pattern.compile("(?i)\\bpage\\s*no\\b")
    ));

    // Common camera photo filename prefixes
    private static final List<String> CAMERA_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            "img_", "dcim_", "pxl_", "photo_", "pic_", "dsc_", "camera_"
    ));

    // Structural document key-value patterns
    private static final List<Pattern> STRUCTURAL_FIELD_PATTERNS = Collections.unmodifiableList(Arrays.asList(
            Pattern.compile("(?i)\\b(?:name|candidate|student|applicant)\\s*[:=]"),
            Pattern.compile("(?i)\\b(?:date|dob|date of birth)\\s*[:=]"),
            Pattern.compile("(?i)\\b(?:address|permanent address)\\s*[:=]"),
            Pattern.compile("(?i)\\b(?:roll no|seat no|registration no|reg no|regn no)\\s*[:=]"),
            Pattern.compile("(?i)\\b(?:amount|total amount|amount paid|subtotal|grand total|fee)\\s*[:=]"),
            Pattern.compile("(?i)\\b(?:chassis no|engine no|vehicle no)\\s*[:=]"),
            Pattern.compile("(?i)\\b(?:receipt no|invoice no|txn id|transaction id)\\s*[:=]"),
            Pattern.compile("(?i)\\b(?:valid upto|valid up to|validity|issue date)\\s*[:=]"),
            Pattern.compile("(?i)\\b(?:signature|authorized signatory)\\b")
    ));

    private static final List<Pattern> DOCUMENT_NUMBER_PATTERNS = Collections.unmodifiableList(Arrays.asList(
            Pattern.compile("\\b\\d{4}\\s\\d{4}\\s\\d{4}\\b"), // Aadhaar format
            Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b"), // PAN format
            Pattern.compile("(?i)\\b(?:AN|AP|AR|AS|BR|CG|CH|DD|DL|DN|GA|GJ|HP|HR|JH|JK|KA|KL|LA|LD|MH|ML|MN|MP|MZ|NL|OD|OR|PB|PY|RJ|SK|TN|TR|TS|UK|UP|UT|WB)\\s*[-]?\\s*[0-9]{1,2}\\s*[-]?\\s*[A-Z]{1,3}\\s*[-]?\\s*[0-9]{4}\\b") // Indian vehicle reg
    ));

    private static final Pattern DATE_PATTERN = Pattern.compile("(?i)\\b(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}(?:st|nd|rd|th)?\\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*)\\b");

    public static boolean isUiMockup(String lowerText, String lowerName) {
        String safeText = lowerText != null ? lowerText : "";
        String safeName = lowerName != null ? lowerName : "";
        for (String kw : UI_MOCKUP_KEYWORDS) {
            if (safeName.contains(kw)) return true;
            Pattern p = Pattern.compile("\\b" + Pattern.quote(kw) + "\\b");
            if (p.matcher(safeText).find()) return true;
        }
        return false;
    }

    public static boolean isNotebookOrSchoolwork(String lowerText, String lowerName) {
        String safeText = lowerText != null ? lowerText : "";
        String safeName = lowerName != null ? lowerName : "";

        int hasNotebookKeyword = 0;
        for (String kw : NOTEBOOK_SCHOOLWORK_KEYWORDS) {
            if (safeName.contains(kw)) {
                hasNotebookKeyword++;
            } else {
                Pattern p = Pattern.compile("\\b" + Pattern.quote(kw) + "\\b");
                if (p.matcher(safeText).find()) {
                    hasNotebookKeyword++;
                }
            }
        }

        int hasMathEquation = 0;
        for (Pattern p : MATH_EQUATION_PATTERNS) {
            if (p.matcher(safeText).find()) {
                hasMathEquation++;
            }
        }

        boolean hasOfficialPhrase = false;
        for (String phrase : STRONG_OFFICIAL_PHRASES) {
            if (safeText.contains(phrase)) {
                hasOfficialPhrase = true;
                break;
            }
        }

        return !hasOfficialPhrase && (hasNotebookKeyword >= 2 || (hasNotebookKeyword >= 1 && hasMathEquation >= 1) || hasMathEquation >= 2);
    }

    public static DetectionResult detect(
            String ocrText,
            String fileName,
            String mimeType
    ) {
        String safeOcr = ocrText != null ? ocrText : "";
        String lowerText = safeOcr.toLowerCase().trim();
        String safeName = fileName != null ? fileName : "";
        String lowerName = safeName.toLowerCase().trim();
        String safeMime = mimeType != null ? mimeType.toLowerCase().trim() : "";

        // 1. Explicit rejection for UI/UX mockups, prototypes, app screens
        if (isUiMockup(lowerText, lowerName)) {
            return new DetectionResult(
                    DocumentDetectionState.NON_DOCUMENT,
                    0.05f,
                    "UI/UX mockup / Prototype design screen detected (Rejected)"
            );
        }

        boolean hasStrongOfficialPhrase = false;
        for (String phrase : STRONG_OFFICIAL_PHRASES) {
            if (lowerText.contains(phrase)) {
                hasStrongOfficialPhrase = true;
                break;
            }
        }

        // 2. Explicit protection against notebook / handwritten homework pages
        if (isNotebookOrSchoolwork(lowerText, lowerName) && !hasStrongOfficialPhrase) {
            boolean isPdf = safeMime.equals("application/pdf") || lowerName.endsWith(".pdf");
            return new DetectionResult(
                    isPdf ? DocumentDetectionState.UNCERTAIN : DocumentDetectionState.NON_DOCUMENT,
                    isPdf ? 0.35f : 0.05f,
                    "Notebook / Handwritten schoolwork / Calculation notes detected"
            );
        }

        // 3. PDF / Office Docs are intrinsically documents
        boolean isPdfOrDoc = safeMime.equals("application/pdf") ||
                lowerName.endsWith(".pdf") ||
                lowerName.endsWith(".doc") ||
                lowerName.endsWith(".docx") ||
                safeMime.startsWith("text/");

        if (isPdfOrDoc) {
            return new DetectionResult(
                    DocumentDetectionState.DOCUMENT,
                    0.95f,
                    "Document MIME type (" + mimeType + ")"
            );
        }

        // 4. Reject obvious non-documents: casual photos, vacations, food, memes
        boolean hasNonDocKeyword = false;
        for (String kw : NON_DOCUMENT_KEYWORDS) {
            if (lowerName.contains(kw)) {
                hasNonDocKeyword = true;
                break;
            }
            Pattern p = Pattern.compile("\\b" + Pattern.quote(kw) + "\\b");
            if (p.matcher(lowerText).find()) {
                hasNonDocKeyword = true;
                break;
            }
        }

        if (hasNonDocKeyword && !hasStrongOfficialPhrase) {
            return new DetectionResult(
                    DocumentDetectionState.NON_DOCUMENT,
                    0.05f,
                    "Casual photo / Non-document subject detected (Rejected)"
            );
        }

        // 5. OCR Text Token Density
        String[] rawTokens = lowerText.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String token : rawTokens) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        int wordCount = tokens.size();

        // Photos with trivial text (< 3 words) are non-documents
        if (wordCount < 3) {
            return new DetectionResult(
                    DocumentDetectionState.NON_DOCUMENT,
                    0.05f,
                    "Photograph / Low text density (< 3 words)"
            );
        }

        boolean isCameraPhoto = false;
        for (String prefix : CAMERA_PREFIXES) {
            if (lowerName.startsWith(prefix)) {
                isCameraPhoto = true;
                break;
            }
        }

        // 6. Strong multi-word official document phrases
        if (hasStrongOfficialPhrase) {
            return new DetectionResult(
                    DocumentDetectionState.DOCUMENT,
                    0.92f,
                    "Strong official document keyphrase detected in content"
            );
        }

        // 7. Structural field matching
        int matchedStructuralFields = 0;
        for (Pattern p : STRUCTURAL_FIELD_PATTERNS) {
            if (p.matcher(lowerText).find()) {
                matchedStructuralFields++;
            }
        }

        boolean hasDocumentNumber = false;
        for (Pattern p : DOCUMENT_NUMBER_PATTERNS) {
            if (p.matcher(safeOcr).find()) {
                hasDocumentNumber = true;
                break;
            }
        }

        if (matchedStructuralFields >= 2 || (matchedStructuralFields >= 1 && hasDocumentNumber)) {
            return new DetectionResult(
                    DocumentDetectionState.DOCUMENT,
                    0.88f,
                    "Document structure and key-value fields detected"
            );
        }

        // 8. Camera photos with incidental text (signboards, t-shirts, posters, casual text)
        if (isCameraPhoto) {
            if (wordCount < 15 && matchedStructuralFields == 0 && !hasDocumentNumber) {
                return new DetectionResult(
                        DocumentDetectionState.NON_DOCUMENT,
                        0.10f,
                        "Casual photograph with incidental text (Rejected)"
                );
            }
        }

        // 9. High text density with structured layout (wordCount >= 20 and date/number pattern)
        boolean hasDatePattern = DATE_PATTERN.matcher(lowerText).find();
        if (wordCount >= 20 && (hasDatePattern || matchedStructuralFields >= 1)) {
            return new DetectionResult(
                    DocumentDetectionState.DOCUMENT,
                    0.75f,
                    "Structured text & document fields detected"
            );
        }

        // 10. Screenshot handling
        boolean isScreenshot = lowerName.contains("screenshot") || lowerName.contains("screen_shot");
        if (isScreenshot) {
            boolean isChatApp = lowerText.contains("online") || lowerText.contains("typing...") ||
                    lowerText.contains("message") || lowerText.contains("forwarded");
            if (isChatApp) {
                return new DetectionResult(
                        DocumentDetectionState.NON_DOCUMENT,
                        0.05f,
                        "Chat / Messaging screenshot (Rejected)"
                );
            }
            if (wordCount >= 8 && (matchedStructuralFields >= 1 || hasDatePattern || hasDocumentNumber)) {
                return new DetectionResult(
                        DocumentDetectionState.DOCUMENT,
                        0.75f,
                        "Document content screenshot detected"
                );
            }
        }

        // 11. Borderline / Uncertain Candidates
        if (wordCount >= 10 && (matchedStructuralFields >= 1 || hasDatePattern)) {
            return new DetectionResult(
                    DocumentDetectionState.UNCERTAIN,
                    0.45f,
                    "Ambiguous document signals (Candidate Review Required)"
            );
        }

        // Default: Casual image with incidental text
        return new DetectionResult(
                DocumentDetectionState.NON_DOCUMENT,
                0.10f,
                "Non-document photograph / Casual image (Rejected)"
        );
    }
}
