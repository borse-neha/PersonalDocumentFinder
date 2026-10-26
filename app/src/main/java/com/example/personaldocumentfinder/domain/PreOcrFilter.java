package com.example.personaldocumentfinder.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PreOcrFilter {

    private PreOcrFilter() {}

    private static final Set<String> DOCUMENT_FILENAME_HINTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "doc", "document", "scan", "scanned", "receipt", "invoice", "ticket",
            "certificate", "aadhaar", "aadhar", "pan", "passport", "bill",
            "form", "statement", "payslip", "hallticket", "hall_ticket", "marksheet",
            "rc", "puc", "license", "licence", "tax", "admit", "degree", "diploma",
            "bonafide", "transcript", "salary", "medical", "insurance", "cheque", "passbook",
            "scholarship", "notice", "circular", "letter", "order", "memo", "marks", "fee"
    )));

    public static boolean isPlausibleCandidate(
            String name,
            String mimeType,
            long fileSize,
            String filePath
    ) {
        if (fileSize <= 0) return false;

        String lowerName = (name != null ? name : "").toLowerCase().trim();
        String lowerPath = (filePath != null ? filePath : "").toLowerCase().trim();
        String safeMime = (mimeType != null ? mimeType : "").toLowerCase().trim();

        // 1. PDF / Office Docs are always plausible
        boolean isPdfOrDoc = safeMime.equals("application/pdf") ||
                lowerName.endsWith(".pdf") ||
                lowerName.endsWith(".doc") ||
                lowerName.endsWith(".docx") ||
                safeMime.startsWith("text/");

        if (isPdfOrDoc) return true;

        // 2. Explicit filename or path document hints
        for (String keyword : DOCUMENT_FILENAME_HINTS) {
            if (lowerName.contains(keyword) || lowerPath.contains(keyword)) {
                return true;
            }
        }

        // 3. Document screenshots
        boolean isScreenshot = lowerName.contains("screenshot") ||
                lowerName.contains("screen_shot") ||
                lowerPath.contains("screenshots");
        if (isScreenshot && fileSize >= 15 * 1024L) return true;

        // 4. Files in Download or Documents folder
        boolean isInDocumentFolder = lowerPath.contains("/download/") ||
                lowerPath.contains("/documents/") ||
                lowerPath.contains("/scans/");
        if (isInDocumentFolder) return true;

        // 5. Image candidate eligibility:
        // Camera images (IMG_*.jpg, PXL_*.jpg, DCIM/*) must NEVER be rejected based on filename.
        // As long as the file is a valid image with >= 15 KB, it is eligible for OCR and document-content detection.
        if (safeMime.startsWith("image/") ||
                lowerName.endsWith(".jpg") ||
                lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") ||
                lowerName.endsWith(".webp")
        ) {
            return fileSize >= 15 * 1024L;
        }

        return false;
    }
}
