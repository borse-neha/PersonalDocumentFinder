package com.example.personaldocumentfinder.domain;

import java.util.Objects;

public class ClassificationResult {
    private final boolean isDocument;
    private final String documentType;
    private final String category;
    private final float documentConfidence;
    private final float categoryConfidence;
    private final float documentTypeConfidence;
    private final String ocrText;
    private final String detectionReason;

    public ClassificationResult(
            boolean isDocument,
            String documentType,
            String category,
            float documentConfidence,
            float categoryConfidence,
            float documentTypeConfidence,
            String ocrText,
            String detectionReason
    ) {
        this.isDocument = isDocument;
        this.documentType = documentType != null ? documentType : "";
        this.category = category != null ? category : "";
        this.documentConfidence = documentConfidence;
        this.categoryConfidence = categoryConfidence;
        this.documentTypeConfidence = documentTypeConfidence;
        this.ocrText = ocrText != null ? ocrText : "";
        this.detectionReason = detectionReason != null ? detectionReason : "";
    }

    public ClassificationResult(
            boolean isDocument,
            String documentType,
            String category,
            float documentConfidence,
            float categoryConfidence
    ) {
        this(isDocument, documentType, category, documentConfidence, categoryConfidence, categoryConfidence, "", "");
    }

    public boolean isDocument() {
        return isDocument;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getCategory() {
        return category;
    }

    public float getDocumentConfidence() {
        return documentConfidence;
    }

    public float getCategoryConfidence() {
        return categoryConfidence;
    }

    public float getDocumentTypeConfidence() {
        return documentTypeConfidence;
    }

    public String getOcrText() {
        return ocrText;
    }

    public String getDetectionReason() {
        return detectionReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassificationResult that = (ClassificationResult) o;
        return isDocument == that.isDocument &&
                Float.compare(that.documentConfidence, documentConfidence) == 0 &&
                Float.compare(that.categoryConfidence, categoryConfidence) == 0 &&
                Float.compare(that.documentTypeConfidence, documentTypeConfidence) == 0 &&
                Objects.equals(documentType, that.documentType) &&
                Objects.equals(category, that.category) &&
                Objects.equals(ocrText, that.ocrText) &&
                Objects.equals(detectionReason, that.detectionReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isDocument, documentType, category, documentConfidence, categoryConfidence, documentTypeConfidence, ocrText, detectionReason);
    }

    @Override
    public String toString() {
        return "ClassificationResult{" +
                "isDocument=" + isDocument +
                ", documentType='" + documentType + '\'' +
                ", category='" + category + '\'' +
                ", documentConfidence=" + documentConfidence +
                ", categoryConfidence=" + categoryConfidence +
                ", documentTypeConfidence=" + documentTypeConfidence +
                ", ocrText='" + ocrText + '\'' +
                ", detectionReason='" + detectionReason + '\'' +
                '}';
    }
}
