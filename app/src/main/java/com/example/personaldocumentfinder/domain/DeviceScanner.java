package com.example.personaldocumentfinder.domain;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeviceScanner {

    private final Context context;

    private static final Set<String> SUPPORTED_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "pdf", "jpg", "jpeg", "png", "txt", "doc", "docx"
    )));

    public interface BatchConsumer {
        void onBatchDiscovered(List<DiscoveredCandidate> batch);
        boolean isCancelled();
    }

    public DeviceScanner(Context context) {
        this.context = context;
    }

    public void scanSharedStorage(int batchSize, BatchConsumer consumer) {
        List<DiscoveredCandidate> discoveredBatch = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        Set<String> seenUris = new HashSet<>();

        // 1. Unified MediaStore Query
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Files.getContentUri("external");
        }

        String[] projection = new String[]{
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DATE_MODIFIED
        };

        String selection = MediaStore.Files.FileColumns.MIME_TYPE + " LIKE ? OR " +
                MediaStore.Files.FileColumns.MIME_TYPE + " LIKE ? OR " +
                MediaStore.Files.FileColumns.MIME_TYPE + " = ? OR " +
                MediaStore.Files.FileColumns.MIME_TYPE + " = ? OR " +
                MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ? OR " +
                MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?";

        String[] selectionArgs = new String[]{
                "image/%",
                "text/%",
                "application/pdf",
                "application/msword",
                "%.pdf",
                "%.docx"
        };

        String sortOrder = MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC";

        try {
            ContentResolver cr = context.getContentResolver();
            if (cr != null) {
                try (Cursor cursor = cr.query(collection, projection, selection, selectionArgs, sortOrder)) {
                    if (cursor != null) {
                        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
                        int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
                        int mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE);
                        int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE);
                        int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
                        int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED);

                        while (cursor.moveToNext()) {
                            if (consumer != null && consumer.isCancelled()) return;

                            long id = cursor.getLong(idColumn);
                            String rawName = cursor.getString(nameColumn);
                            String name = (rawName != null && !rawName.trim().isEmpty()) ? rawName : ("Document_" + id);
                            String rawMime = cursor.getString(mimeColumn);
                            String mimeType = (rawMime != null && !rawMime.trim().isEmpty()) ? rawMime : getMimeTypeFromExtension(name);
                            long size = cursor.getLong(sizeColumn);
                            String rawPath = cursor.getString(dataColumn);
                            String filePath = rawPath != null ? rawPath : "";
                            long dateModified = cursor.getLong(dateColumn) * 1000L;

                            int dotIdx = name.lastIndexOf('.');
                            String ext = dotIdx != -1 ? name.substring(dotIdx + 1).toLowerCase() : "";

                            if (SUPPORTED_EXTENSIONS.contains(ext) || mimeType.startsWith("image/") || mimeType.equals("application/pdf")) {
                                Uri contentUri = ContentUris.withAppendedId(collection, id);
                                String uriString = contentUri.toString();

                                if (size > 0 && seenUris.add(uriString) && (filePath.trim().isEmpty() || seenPaths.add(filePath))) {
                                    discoveredBatch.add(new DiscoveredCandidate(
                                            contentUri,
                                            name,
                                            mimeType,
                                            size,
                                            filePath,
                                            dateModified
                                    ));

                                    if (discoveredBatch.size() >= batchSize) {
                                        if (consumer != null) {
                                            consumer.onBatchDiscovered(new ArrayList<>(discoveredBatch));
                                        }
                                        discoveredBatch.clear();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (consumer != null && consumer.isCancelled()) return;

        // 2. Secondary Direct Folder Check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            File rootExternal = Environment.getExternalStorageDirectory();
            List<File> targetDirs = Arrays.asList(
                    new File(rootExternal, "Download"),
                    new File(rootExternal, "Documents"),
                    new File(rootExternal, "Pictures"),
                    new File(rootExternal, "DCIM"),
                    new File(rootExternal, "Android/media/com.whatsapp/WhatsApp/Media")
            );

            for (File dir : targetDirs) {
                if (consumer != null && consumer.isCancelled()) return;
                if (dir.exists() && dir.canRead()) {
                    scanDirectoryRecursive(dir, 0, 3, seenPaths, seenUris, discoveredBatch, batchSize, consumer);
                }
            }
        }

        // Emit remaining items
        if (!discoveredBatch.isEmpty()) {
            if (consumer != null && !consumer.isCancelled()) {
                consumer.onBatchDiscovered(new ArrayList<>(discoveredBatch));
            }
            discoveredBatch.clear();
        }
    }

    private void scanDirectoryRecursive(
            File dir,
            int currentDepth,
            int maxDepth,
            Set<String> seenPaths,
            Set<String> seenUris,
            List<DiscoveredCandidate> discoveredBatch,
            int batchSize,
            BatchConsumer consumer
    ) {
        if (consumer != null && consumer.isCancelled()) return;
        if (currentDepth > maxDepth) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (consumer != null && consumer.isCancelled()) return;
            if (file.isDirectory()) {
                scanDirectoryRecursive(file, currentDepth + 1, maxDepth, seenPaths, seenUris, discoveredBatch, batchSize, consumer);
            } else if (file.isFile() && file.length() > 0) {
                int dotIdx = file.getName().lastIndexOf('.');
                String ext = dotIdx != -1 ? file.getName().substring(dotIdx + 1).toLowerCase() : "";
                if (SUPPORTED_EXTENSIONS.contains(ext)) {
                    if (seenPaths.add(file.getAbsolutePath())) {
                        Uri fileUri = Uri.fromFile(file);
                        if (seenUris.add(fileUri.toString())) {
                            discoveredBatch.add(new DiscoveredCandidate(
                                    fileUri,
                                    file.getName(),
                                    getMimeTypeFromExtension(ext),
                                    file.length(),
                                    file.getAbsolutePath(),
                                    file.lastModified()
                            ));

                            if (discoveredBatch.size() >= batchSize) {
                                if (consumer != null) {
                                    consumer.onBatchDiscovered(new ArrayList<>(discoveredBatch));
                                }
                                discoveredBatch.clear();
                            }
                        }
                    }
                }
            }
        }
    }

    public static String getMimeTypeFromExtension(String ext) {
        if (ext == null) return "application/octet-stream";
        int dot = ext.lastIndexOf('.');
        String clean = (dot != -1 ? ext.substring(dot + 1) : ext).toLowerCase();
        switch (clean) {
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
