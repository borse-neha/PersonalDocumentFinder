package com.example.personaldocumentfinder.domain;

import android.net.Uri;
import java.util.Objects;

public class DiscoveredCandidate {
    private final Uri uri;
    private final String name;
    private final String mimeType;
    private final long fileSize;
    private final String filePath;
    private final long dateModified;

    public DiscoveredCandidate(
            Uri uri,
            String name,
            String mimeType,
            long fileSize,
            String filePath,
            long dateModified
    ) {
        this.uri = uri;
        this.name = name != null ? name : "";
        this.mimeType = mimeType != null ? mimeType : "";
        this.fileSize = fileSize;
        this.filePath = filePath != null ? filePath : "";
        this.dateModified = dateModified;
    }

    public Uri getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getDateModified() {
        return dateModified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscoveredCandidate that = (DiscoveredCandidate) o;
        return fileSize == that.fileSize &&
                dateModified == that.dateModified &&
                Objects.equals(uri, that.uri) &&
                Objects.equals(name, that.name) &&
                Objects.equals(mimeType, that.mimeType) &&
                Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, name, mimeType, fileSize, filePath, dateModified);
    }

    @Override
    public String toString() {
        return "DiscoveredCandidate{" +
                "uri=" + uri +
                ", name='" + name + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", fileSize=" + fileSize +
                ", filePath='" + filePath + '\'' +
                ", dateModified=" + dateModified +
                '}';
    }
}
