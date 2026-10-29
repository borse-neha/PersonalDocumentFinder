package com.example.personaldocumentfinder.domain;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Objects;

public class StorageManager {

    private final Context context;

    public StorageManager(Context context) {
        this.context = context;
    }

    public static class StorageStats {
        private final long totalBytes;
        private final int fileCount;

        public StorageStats(long totalBytes, int fileCount) {
            this.totalBytes = totalBytes;
            this.fileCount = fileCount;
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public int getFileCount() {
            return fileCount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StorageStats that = (StorageStats) o;
            return totalBytes == that.totalBytes && fileCount == that.fileCount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(totalBytes, fileCount);
        }

        @Override
        public String toString() {
            return "StorageStats{" +
                    "totalBytes=" + totalBytes +
                    ", fileCount=" + fileCount +
                    '}';
        }
    }

    public static class CopyResult {
        private final String storedFileName;
        private final String internalPath;
        private final long fileSize;
        private final String mimeType;
        private final String contentHash;

        public CopyResult(
                String storedFileName,
                String internalPath,
                long fileSize,
                String mimeType,
                String contentHash
        ) {
            this.storedFileName = storedFileName != null ? storedFileName : "";
            this.internalPath = internalPath != null ? internalPath : "";
            this.fileSize = fileSize;
            this.mimeType = mimeType != null ? mimeType : "";
            this.contentHash = contentHash != null ? contentHash : "";
        }

        public String getStoredFileName() {
            return storedFileName;
        }

        public String getInternalPath() {
            return internalPath;
        }

        public long getFileSize() {
            return fileSize;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getContentHash() {
            return contentHash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CopyResult that = (CopyResult) o;
            return fileSize == that.fileSize &&
                    Objects.equals(storedFileName, that.storedFileName) &&
                    Objects.equals(internalPath, that.internalPath) &&
                    Objects.equals(mimeType, that.mimeType) &&
                    Objects.equals(contentHash, that.contentHash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(storedFileName, internalPath, fileSize, mimeType, contentHash);
        }

        @Override
        public String toString() {
            return "CopyResult{" +
                    "storedFileName='" + storedFileName + '\'' +
                    ", internalPath='" + internalPath + '\'' +
                    ", fileSize=" + fileSize +
                    ", mimeType='" + mimeType + '\'' +
                    ", contentHash='" + contentHash + '\'' +
                    '}';
        }
    }

    public InputStream openUriInputStream(Uri uri) throws Exception {
        if (uri == null) return null;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return new FileInputStream(new File(uri.getPath()));
        }
        return context != null && context.getContentResolver() != null
                ? context.getContentResolver().openInputStream(uri)
                : null;
    }

    public String calculateContentHash(Uri uri) {
        if (uri == null) return null;
        try (InputStream inputStream = openUriInputStream(uri)) {
            if (inputStream == null) return null;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public File getOrganizedBaseDir() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    File documentsPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                    File baseAppDir = new File(documentsPublicDir, "Personal Document Finder");
                    if ((baseAppDir.exists() || baseAppDir.mkdirs()) && canWriteToDir(baseAppDir)) {
                        return baseAppDir;
                    }
                }
                return getFallbackDir();
            } else {
                File documentsPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File baseAppDir = new File(documentsPublicDir, "Personal Document Finder");
                if ((baseAppDir.exists() || baseAppDir.mkdirs()) && canWriteToDir(baseAppDir)) {
                    return baseAppDir;
                } else {
                    return getFallbackDir();
                }
            }
        } catch (Throwable t) {
            return getFallbackDir();
        }
    }

    private boolean canWriteToDir(File dir) {
        if (dir == null) return false;
        try {
            File testFile = new File(dir, ".test_write_" + System.currentTimeMillis());
            if (testFile.createNewFile()) {
                testFile.delete();
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private File getFallbackDir() {
        try {
            File extFiles = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (extFiles != null) {
                File dir = new File(extFiles, "Personal Document Finder");
                if ((dir.exists() || dir.mkdirs()) && canWriteToDir(dir)) return dir;
            }
            File internal = new File(context.getFilesDir(), "Documents/Personal Document Finder");
            if (!internal.exists()) internal.mkdirs();
            return internal;
        } catch (Throwable t) {
            File internal = new File(context.getFilesDir(), "Documents/Personal Document Finder");
            if (!internal.exists()) internal.mkdirs();
            return internal;
        }
    }

    public File getCategoryDir(String category) {
        String sanitizedCategory = sanitizeCategory(category);
        File dir = new File(getOrganizedBaseDir(), sanitizedCategory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public CopyResult copyFileToOrganizedStorage(Uri uri, String category, String customFileName) {
        if (uri == null) return null;
        try {
            try {
                if (context != null && context.getContentResolver() != null && "content".equals(uri.getScheme())) {
                    context.getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                }
            } catch (Exception ignored) {}

            String originalName = (customFileName != null && !customFileName.trim().isEmpty())
                    ? customFileName
                    : getFileNameFromUri(uri);
            File categoryDir = getCategoryDir(category);
            if (!categoryDir.exists() && !categoryDir.mkdirs()) {
                categoryDir = new File(getFallbackDir(), sanitizeCategory(category));
                categoryDir.mkdirs();
            }

            String storedFileName = generateUniqueFileName(categoryDir, originalName);
            File destFile = new File(categoryDir, storedFileName);

            long fileSize = 0L;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream inputStream = openUriInputStream(uri)) {
                if (inputStream == null) return null;
                FileOutputStream outputStream = null;
                try {
                    outputStream = new FileOutputStream(destFile);
                } catch (Exception e) {
                    // Fall back to app-private fallback dir if public folder write failed
                    File fallbackCatDir = new File(getFallbackDir(), sanitizeCategory(category));
                    fallbackCatDir.mkdirs();
                    storedFileName = generateUniqueFileName(fallbackCatDir, originalName);
                    destFile = new File(fallbackCatDir, storedFileName);
                    outputStream = new FileOutputStream(destFile);
                }

                try {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        digest.update(buffer, 0, bytesRead);
                        fileSize += bytesRead;
                    }
                } finally {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                }
            }

            if (fileSize <= 0L) {
                if (destFile.exists()) destFile.delete();
                return null;
            }

            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            String contentHash = sb.toString();

            String typeFromCr = context.getContentResolver().getType(uri);
            String mimeType = typeFromCr != null ? typeFromCr : getMimeTypeFromExtension(getExtension(destFile.getName()));

            // Notify MediaScanner so the organized file is immediately discoverable in phone's Files app
            try {
                MediaScannerConnection.scanFile(context, new String[]{destFile.getAbsolutePath()}, new String[]{mimeType}, null);
            } catch (Throwable ignored) {}

            return new CopyResult(
                    storedFileName,
                    destFile.getAbsolutePath(),
                    fileSize,
                    mimeType,
                    contentHash
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public CopyResult copyFileToOrganizedStorage(Uri uri, String category) {
        return copyFileToOrganizedStorage(uri, category, null);
    }

    // For backwards compatibility with existing callers
    public CopyResult copyFileToAppPrivateStorage(Uri uri, String category, String customFileName) {
        return copyFileToOrganizedStorage(uri, category, customFileName);
    }

    public CopyResult copyFileToAppPrivateStorage(Uri uri, String category) {
        return copyFileToOrganizedStorage(uri, category, null);
    }

    public String moveFileToCategory(String internalPath, String newCategory) {
        if (internalPath == null) return null;
        try {
            File currentFile = new File(internalPath);
            if (!currentFile.exists()) return null;

            File newCategoryDir = getCategoryDir(newCategory);
            if (currentFile.getParentFile() != null &&
                    currentFile.getParentFile().getCanonicalPath().equals(newCategoryDir.getCanonicalPath())) {
                return currentFile.getAbsolutePath();
            }

            String newStoredFileName = generateUniqueFileName(newCategoryDir, currentFile.getName());
            File newFile = new File(newCategoryDir, newStoredFileName);

            boolean success = false;
            if (currentFile.renameTo(newFile)) {
                success = true;
            } else {
                copyFile(currentFile, newFile);
                currentFile.delete();
                success = true;
            }

            if (success) {
                try {
                    MediaScannerConnection.scanFile(context, new String[]{newFile.getAbsolutePath(), currentFile.getAbsolutePath()}, null, null);
                } catch (Throwable ignored) {}
                return newFile.getAbsolutePath();
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    public boolean deleteAppPrivateFile(String internalPath) {
        if (internalPath == null) return false;
        try {
            File file = new File(internalPath);
            if (!file.exists()) return true;

            // Strict boundary safety check:
            // Ensure the file is strictly inside the app's organized storage or internal files dir.
            // Under NO circumstance should any external user directory outside our managed folders be deleted!
            String canonicalPath = file.getCanonicalPath();
            String baseOrganized = getOrganizedBaseDir().getCanonicalPath();
            String internalFiles = context.getFilesDir().getCanonicalPath();
            File extFilesDir = context.getExternalFilesDir(null);
            String extFiles = extFilesDir != null ? extFilesDir.getCanonicalPath() : null;

            boolean isAllowed = canonicalPath.startsWith(baseOrganized) ||
                    canonicalPath.startsWith(internalFiles) ||
                    (extFiles != null && canonicalPath.startsWith(extFiles));

            if (!isAllowed) {
                // Reject deletion outside app boundary to protect external user data!
                return false;
            }

            boolean deleted = file.delete();
            try {
                MediaScannerConnection.scanFile(context, new String[]{internalPath}, null, null);
            } catch (Throwable ignored) {}
            return deleted;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public StorageStats getStorageStats() {
        long[] totalBytes = new long[]{0L};
        int[] fileCount = new int[]{0};

        calculateDir(getOrganizedBaseDir(), totalBytes, fileCount);
        return new StorageStats(totalBytes[0], fileCount[0]);
    }

    private void calculateDir(File dir, long[] totalBytes, int[] fileCount) {
        if (dir == null) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                calculateDir(file, totalBytes, fileCount);
            } else if (file.isFile()) {
                totalBytes[0] += file.length();
                fileCount[0]++;
            }
        }
    }

    public String getFileNameFromUri(Uri uri) {
        if (uri == null) return "Document_" + System.currentTimeMillis();
        String name = null;
        if ("content".equals(uri.getScheme()) && context != null && context.getContentResolver() != null) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (name == null || name.trim().isEmpty()) {
            String lastPath = uri.getLastPathSegment();
            if (lastPath != null) {
                int idx = lastPath.lastIndexOf('/');
                name = idx != -1 ? lastPath.substring(idx + 1) : lastPath;
            }
        }
        return (name != null && !name.trim().isEmpty()) ? name : ("Document_" + System.currentTimeMillis());
    }

    private String sanitizeCategory(String category) {
        if (category == null) return "Other Documents";
        String trimmed = category.trim();
        switch (trimmed) {
            case "College":
            case "Identity":
            case "Finance":
            case "Office":
            case "Vehicle":
            case "Personal":
                return trimmed;
            case "Other":
            case "Other Documents":
                return "Other Documents";
            default:
                return trimmed.replaceAll("[^a-zA-Z0-9_ -]", "_");
        }
    }

    private String generateUniqueFileName(File dir, String originalName) {
        File file = new File(dir, originalName);
        if (!file.exists()) return originalName;

        int dotIdx = originalName.lastIndexOf('.');
        String nameWithoutExt = dotIdx != -1 ? originalName.substring(0, dotIdx) : originalName;
        String ext = dotIdx != -1 ? originalName.substring(dotIdx + 1) : "";
        String extPart = !ext.isEmpty() ? ("." + ext) : "";

        int counter = 1;
        while (file.exists()) {
            file = new File(dir, nameWithoutExt + "_" + counter + extPart);
            counter++;
        }
        return file.getName();
    }

    public static String getExtension(String fileName) {
        if (fileName == null) return "";
        int dotIdx = fileName.lastIndexOf('.');
        return dotIdx != -1 ? fileName.substring(dotIdx + 1) : "";
    }

    public String getMimeTypeFromExtension(String ext) {
        if (ext == null) return "application/octet-stream";
        switch (ext.toLowerCase()) {
            case "pdf":
                return "application/pdf";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "txt":
                return "text/plain";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default:
                return "application/octet-stream";
        }
    }
}
