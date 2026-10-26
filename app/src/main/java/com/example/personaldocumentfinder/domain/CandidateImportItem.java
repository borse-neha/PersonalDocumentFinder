package com.example.personaldocumentfinder.domain;

import android.net.Uri;
import java.util.Objects;

public class CandidateImportItem {
    private final Uri uri;
    private final String fileName;
    private final ClassificationResult classification;
    private final String selectedCategory;
    private final boolean isDuplicate;

    public CandidateImportItem(
            Uri uri,
            String fileName,
            ClassificationResult classification,
            String selectedCategory,
            boolean isDuplicate
    ) {
        this.uri = uri;
        this.fileName = fileName != null ? fileName : "";
        this.classification = classification;
        this.selectedCategory = selectedCategory != null ? selectedCategory : (classification != null ? classification.getCategory() : "");
        this.isDuplicate = isDuplicate;
    }

    public CandidateImportItem(
            Uri uri,
            String fileName,
            ClassificationResult classification
    ) {
        this(
                uri,
                fileName,
                classification,
                classification != null ? classification.getCategory() : "",
                false
        );
    }

    public Uri getUri() {
        return uri;
    }

    public String getFileName() {
        return fileName;
    }

    public ClassificationResult getClassification() {
        return classification;
    }

    public String getSelectedCategory() {
        return selectedCategory;
    }

    public boolean isDuplicate() {
        return isDuplicate;
    }

    public CandidateImportItem copy(String newSelectedCategory) {
        return new CandidateImportItem(
                uri,
                fileName,
                classification,
                newSelectedCategory,
                isDuplicate
        );
    }

    public CandidateImportItem copy(String newSelectedCategory, boolean newIsDuplicate) {
        return new CandidateImportItem(
                uri,
                fileName,
                classification,
                newSelectedCategory,
                newIsDuplicate
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CandidateImportItem that = (CandidateImportItem) o;
        return isDuplicate == that.isDuplicate &&
                Objects.equals(uri, that.uri) &&
                Objects.equals(fileName, that.fileName) &&
                Objects.equals(classification, that.classification) &&
                Objects.equals(selectedCategory, that.selectedCategory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, fileName, classification, selectedCategory, isDuplicate);
    }

    @Override
    public String toString() {
        return "CandidateImportItem{" +
                "uri=" + uri +
                ", fileName='" + fileName + '\'' +
                ", classification=" + classification +
                ", selectedCategory='" + selectedCategory + '\'' +
                ", isDuplicate=" + isDuplicate +
                '}';
    }
}
