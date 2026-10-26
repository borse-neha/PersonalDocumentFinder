package com.example.personaldocumentfinder.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(tableName = "documents")
public class DocumentEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;
    private String originalName;
    private String storedFileName;
    private String displayName;
    private String mimeType;
    private String originalUri;
    private String internalPath;
    private String category; // College, Identity, Finance, Office, Vehicle, Personal, Other Documents
    private String documentType; // e.g. "Aadhaar Card", "Hall Ticket", "Receipt", "Unclassified Document"
    private float documentConfidence;
    private float categoryConfidence;
    private String ocrText;
    private long fileSize;
    private long dateImported;
    private long lastModified;
    private boolean isFavorite;
    private boolean isReviewed;
    private String contentHash;

    public DocumentEntity() {
        this.displayName = "";
        this.documentConfidence = 0.0f;
        this.categoryConfidence = 0.0f;
        this.ocrText = "";
        this.fileSize = 0L;
        this.dateImported = System.currentTimeMillis();
        this.lastModified = System.currentTimeMillis();
        this.isFavorite = false;
        this.isReviewed = true;
        this.contentHash = "";
    }

    public DocumentEntity(
            long id,
            String originalName,
            String storedFileName,
            String displayName,
            String mimeType,
            String originalUri,
            String internalPath,
            String category,
            String documentType,
            float documentConfidence,
            float categoryConfidence,
            String ocrText,
            long fileSize,
            long dateImported,
            long lastModified,
            boolean isFavorite,
            boolean isReviewed,
            String contentHash
    ) {
        this.id = id;
        this.originalName = originalName != null ? originalName : "";
        this.storedFileName = storedFileName != null ? storedFileName : "";
        this.displayName = displayName != null ? displayName : "";
        this.mimeType = mimeType != null ? mimeType : "";
        this.originalUri = originalUri != null ? originalUri : "";
        this.internalPath = internalPath != null ? internalPath : "";
        this.category = category != null ? category : "";
        this.documentType = documentType != null ? documentType : "";
        this.documentConfidence = documentConfidence;
        this.categoryConfidence = categoryConfidence;
        this.ocrText = ocrText != null ? ocrText : "";
        this.fileSize = fileSize;
        this.dateImported = dateImported;
        this.lastModified = lastModified;
        this.isFavorite = isFavorite;
        this.isReviewed = isReviewed;
        this.contentHash = contentHash != null ? contentHash : "";
    }

    @Ignore
    public DocumentEntity(
            String originalName,
            String storedFileName,
            String displayName,
            String mimeType,
            String originalUri,
            String internalPath,
            String category,
            String documentType
    ) {
        this(
                0L,
                originalName,
                storedFileName,
                displayName,
                mimeType,
                originalUri,
                internalPath,
                category,
                documentType,
                0.0f,
                0.0f,
                "",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                ""
        );
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName != null ? originalName : "";
    }

    public String getStoredFileName() {
        return storedFileName;
    }

    public void setStoredFileName(String storedFileName) {
        this.storedFileName = storedFileName != null ? storedFileName : "";
    }

    public String getDisplayName() {
        return displayName != null ? displayName : "";
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName != null ? displayName : "";
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType != null ? mimeType : "";
    }

    public String getOriginalUri() {
        return originalUri;
    }

    public void setOriginalUri(String originalUri) {
        this.originalUri = originalUri != null ? originalUri : "";
    }

    public String getInternalPath() {
        return internalPath;
    }

    public void setInternalPath(String internalPath) {
        this.internalPath = internalPath != null ? internalPath : "";
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category != null ? category : "";
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType != null ? documentType : "";
    }

    public float getDocumentConfidence() {
        return documentConfidence;
    }

    public void setDocumentConfidence(float documentConfidence) {
        this.documentConfidence = documentConfidence;
    }

    public float getCategoryConfidence() {
        return categoryConfidence;
    }

    public void setCategoryConfidence(float categoryConfidence) {
        this.categoryConfidence = categoryConfidence;
    }

    public String getOcrText() {
        return ocrText != null ? ocrText : "";
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText != null ? ocrText : "";
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getDateImported() {
        return dateImported;
    }

    public void setDateImported(long dateImported) {
        this.dateImported = dateImported;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public boolean isReviewed() {
        return isReviewed;
    }

    public void setReviewed(boolean reviewed) {
        isReviewed = reviewed;
    }

    public String getContentHash() {
        return contentHash != null ? contentHash : "";
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash != null ? contentHash : "";
    }

    public String getEffectiveDisplayName() {
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        } else if (documentType != null && !documentType.trim().isEmpty() &&
                !documentType.equals("Unclassified Document") &&
                !documentType.equals("Photograph / Non-Document")) {
            return documentType;
        } else {
            return originalName != null ? originalName : "";
        }
    }

    public DocumentEntity copy() {
        return new DocumentEntity(
                id, originalName, storedFileName, displayName, mimeType,
                originalUri, internalPath, category, documentType,
                documentConfidence, categoryConfidence, ocrText,
                fileSize, dateImported, lastModified, isFavorite,
                isReviewed, contentHash
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentEntity that = (DocumentEntity) o;
        return id == that.id &&
                Float.compare(that.documentConfidence, documentConfidence) == 0 &&
                Float.compare(that.categoryConfidence, categoryConfidence) == 0 &&
                fileSize == that.fileSize &&
                dateImported == that.dateImported &&
                lastModified == that.lastModified &&
                isFavorite == that.isFavorite &&
                isReviewed == that.isReviewed &&
                Objects.equals(originalName, that.originalName) &&
                Objects.equals(storedFileName, that.storedFileName) &&
                Objects.equals(displayName, that.displayName) &&
                Objects.equals(mimeType, that.mimeType) &&
                Objects.equals(originalUri, that.originalUri) &&
                Objects.equals(internalPath, that.internalPath) &&
                Objects.equals(category, that.category) &&
                Objects.equals(documentType, that.documentType) &&
                Objects.equals(ocrText, that.ocrText) &&
                Objects.equals(contentHash, that.contentHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, originalName, storedFileName, displayName, mimeType,
                originalUri, internalPath, category, documentType,
                documentConfidence, categoryConfidence, ocrText,
                fileSize, dateImported, lastModified, isFavorite,
                isReviewed, contentHash);
    }

    @Override
    public String toString() {
        return "DocumentEntity{" +
                "id=" + id +
                ", originalName='" + originalName + '\'' +
                ", storedFileName='" + storedFileName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", originalUri='" + originalUri + '\'' +
                ", internalPath='" + internalPath + '\'' +
                ", category='" + category + '\'' +
                ", documentType='" + documentType + '\'' +
                ", documentConfidence=" + documentConfidence +
                ", categoryConfidence=" + categoryConfidence +
                ", ocrText='" + ocrText + '\'' +
                ", fileSize=" + fileSize +
                ", dateImported=" + dateImported +
                ", lastModified=" + lastModified +
                ", isFavorite=" + isFavorite +
                ", isReviewed=" + isReviewed +
                ", contentHash='" + contentHash + '\'' +
                '}';
    }
}
