package com.example.personaldocumentfinder.domain;

import java.util.Objects;

public class DetectionResult {
    private final DocumentDetectionState state;
    private final float documentConfidence;
    private final String detectionReason;

    public DetectionResult(DocumentDetectionState state, float documentConfidence, String detectionReason) {
        this.state = state;
        this.documentConfidence = documentConfidence;
        this.detectionReason = detectionReason != null ? detectionReason : "";
    }

    public DocumentDetectionState getState() {
        return state;
    }

    public float getDocumentConfidence() {
        return documentConfidence;
    }

    public String getDetectionReason() {
        return detectionReason;
    }

    public boolean isDocument() {
        return state == DocumentDetectionState.DOCUMENT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DetectionResult that = (DetectionResult) o;
        return Float.compare(that.documentConfidence, documentConfidence) == 0 &&
                state == that.state &&
                Objects.equals(detectionReason, that.detectionReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, documentConfidence, detectionReason);
    }

    @Override
    public String toString() {
        return "DetectionResult{" +
                "state=" + state +
                ", documentConfidence=" + documentConfidence +
                ", detectionReason='" + detectionReason + '\'' +
                '}';
    }
}
