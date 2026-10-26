package com.example.personaldocumentfinder.domain;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;

public final class FileOpener {

    private FileOpener() {}

    public static abstract class OpenResult {
        public static final class Success extends OpenResult {
            public static final Success INSTANCE = new Success();
            private Success() {}
        }

        public static final class Error extends OpenResult {
            private final String message;

            public Error(String message) {
                this.message = message != null ? message : "";
            }

            public String getMessage() {
                return message;
            }
        }
    }

    public static OpenResult openDocument(Context context, String internalPath, String mimeType) {
        File file = new File(internalPath != null ? internalPath : "");
        if (!file.exists()) {
            return new OpenResult.Error("Document file no longer exists in app storage.");
        }

        try {
            String authority = context.getPackageName() + ".fileprovider";
            Uri uri = FileProvider.getUriForFile(context, authority, file);

            String safeMime = (mimeType != null && !mimeType.trim().isEmpty()) ? mimeType : "*/*";

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, safeMime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);
            return OpenResult.Success.INSTANCE;
        } catch (ActivityNotFoundException e) {
            return new OpenResult.Error("No compatible app found to open this document type (" + mimeType + ").");
        } catch (Exception e) {
            String msg = e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Unknown error";
            return new OpenResult.Error("Failed to open document: " + msg);
        }
    }
}
