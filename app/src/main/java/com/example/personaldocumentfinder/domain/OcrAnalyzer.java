package com.example.personaldocumentfinder.domain;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class OcrAnalyzer {

    private OcrAnalyzer() {}

    private static volatile TextRecognizer recognizer;

    private static TextRecognizer getRecognizer() {
        if (recognizer == null) {
            synchronized (OcrAnalyzer.class) {
                if (recognizer == null) {
                    recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                }
            }
        }
        return recognizer;
    }

    public static String extractText(Context context, Uri uri, String mimeType) {
        try {
            String safeMime = mimeType != null ? mimeType : "";
            if (safeMime.startsWith("image/")) {
                return extractFromImageUri(context, uri);
            } else if (safeMime.equals("application/pdf")) {
                return extractFromPdfUri(context, uri);
            } else if (safeMime.startsWith("text/")) {
                return extractFromTextUri(context, uri);
            } else {
                return extractFromImageUri(context, uri);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String extractFromBitmap(Bitmap bitmap) {
        if (bitmap == null) return "";
        try {
            InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
            Text visionText = Tasks.await(getRecognizer().process(inputImage));
            return visionText != null && visionText.getText() != null ? visionText.getText() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String extractFromImageUri(Context context, Uri uri) {
        if (context == null || uri == null) return "";
        try {
            InputImage inputImage = InputImage.fromFilePath(context, uri);
            Text visionText = Tasks.await(getRecognizer().process(inputImage));
            return visionText != null && visionText.getText() != null ? visionText.getText() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String extractFromPdfUri(Context context, Uri uri) {
        if (context == null || uri == null) return "";
        StringBuilder extractedText = new StringBuilder();
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;

        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                pfd = ParcelFileDescriptor.open(new java.io.File(uri.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            }
            if (pfd != null) {
                renderer = new PdfRenderer(pfd);
                int pageCount = Math.min(renderer.getPageCount(), 5);

                for (int i = 0; i < pageCount; i++) {
                    PdfRenderer.Page page = null;
                    try {
                        page = renderer.openPage(i);
                        int width = page.getWidth();
                        int height = page.getHeight();
                        Bitmap bitmap = Bitmap.createBitmap(
                                Math.min(width, 1080),
                                Math.min(height, 1920),
                                Bitmap.Config.ARGB_8888
                        );
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                        String pageText = extractFromBitmap(bitmap);
                        if (!pageText.trim().isEmpty()) {
                            extractedText.append(pageText).append("\n\n");
                        }
                        bitmap.recycle();
                    } finally {
                        if (page != null) {
                            try { page.close(); } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (renderer != null) {
                try { renderer.close(); } catch (Exception ignored) {}
            }
            if (pfd != null) {
                try { pfd.close(); } catch (Exception ignored) {}
            }
        }

        return extractedText.toString().trim();
    }

    private static String extractFromTextUri(Context context, Uri uri) {
        if (context == null || uri == null) return "";
        InputStream is = null;
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                is = new java.io.FileInputStream(new java.io.File(uri.getPath()));
            } else {
                is = context.getContentResolver().openInputStream(uri);
            }
        } catch (Exception ignored) {}
        try (InputStream inputStream = is) {
            if (inputStream == null) return "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
