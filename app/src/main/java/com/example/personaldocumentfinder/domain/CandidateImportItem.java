package com.example.personaldocumentfinder.domain;

import android.net.Uri;
import java.util.Objects;

public class CandidateImportItem {
    private final Uri uri;
    private final String fileName;
    private final ClassificationResult classification;
    private final String selectedCategory;
    private final boolean isDuplicate;
    private final String displayName;
    private final String duplicateOfName;

    public CandidateImportItem(
            Uri uri,
            String fileName,
            ClassificationResult classification,
            String selectedCategory,
            boolean isDuplicate,
            String displayName,
            String duplicateOfName
    ) {
        this.uri = uri;
        this.fileName = fileName != null ? fileName : "";
        this.classification = classification;
        this.selectedCategory = selectedCategory != null ? selectedCategory : (classification != null ? classification.getCategory() : "");
        this.isDuplicate = isDuplicate;
        this.displayName = displayName != null && !displayName.trim().isEmpty() ? displayName : this.fileName;
        this.duplicateOfName = duplicateOfName;
    }

    public CandidateImportItem(
            Uri uri,
            String fileName,
            ClassificationResult classification,
            String selectedCategory,
            boolean isDuplicate
    ) {
        this(
                uri,
                fileName,
                classification,
                selectedCategory,
                isDuplicate,
                fileName,
                null
        );
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
                false,
                fileName,
                null
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

    public String getDisplayName() {
        return displayName != null ? displayName : fileName;
    }

    public String getDuplicateOfName() {
        return duplicateOfName;
    }

    public CandidateImportItem copy(String newSelectedCategory) {
        return new CandidateImportItem(
                uri,
                fileName,
                classification,
                newSelectedCategory,
                isDuplicate,
                displayName,
                duplicateOfName
        );
    }

    public CandidateImportItem copy(String newSelectedCategory, boolean newIsDuplicate) {
        return new CandidateImportItem(
                uri,
                fileName,
                classification,
                newSelectedCategory,
                newIsDuplicate,
                displayName,
                duplicateOfName
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
                Objects.equals(selectedCategory, that.selectedCategory) &&
                Objects.equals(displayName, that.displayName) &&
                Objects.equals(duplicateOfName, that.duplicateOfName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, fileName, classification, selectedCategory, isDuplicate, displayName, duplicateOfName);
    }

    @Override
    public String toString() {
        return "CandidateImportItem{" +
                "uri=" + uri +
                ", fileName='" + fileName + '\'' +
                ", classification=" + classification +
                ", selectedCategory='" + selectedCategory + '\'' +
                ", isDuplicate=" + isDuplicate +
                ", displayName='" + displayName + '\'' +
                ", duplicateOfName='" + duplicateOfName + '\'' +
                '}';
    }
}
