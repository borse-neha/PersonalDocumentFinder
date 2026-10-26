package com.example.personaldocumentfinder;

import com.example.personaldocumentfinder.domain.ClassificationResult;
import com.example.personaldocumentfinder.domain.DetectionResult;
import com.example.personaldocumentfinder.domain.DocumentClassifier;
import com.example.personaldocumentfinder.domain.DocumentDetectionState;
import com.example.personaldocumentfinder.domain.DocumentDetector;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DocumentDetectorTest {

    @Test
    public void testNormalPhotoRejection() {
        String ocrText = "";
        String fileName = "IMG_20260924_102030.jpg";
        String mimeType = "image/jpeg";

        DetectionResult detection = DocumentDetector.detect(ocrText, fileName, mimeType);
        assertEquals(DocumentDetectionState.NON_DOCUMENT, detection.getState());
        assertFalse(detection.isDocument());

        ClassificationResult classification = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertFalse(classification.isDocument());
    }

    @Test
    public void testSingleIncidentalWordPhotoRejection() {
        // Photo of a cereal box or signboard with just the word "bill" or "tax"
        String ocrText = "super bill";
        String fileName = "IMG_9912.jpg";
        String mimeType = "image/jpeg";

        DetectionResult detection = DocumentDetector.detect(ocrText, fileName, mimeType);
        assertEquals(DocumentDetectionState.NON_DOCUMENT, detection.getState());
        assertFalse(detection.isDocument());

        ClassificationResult classification = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertFalse(classification.isDocument());
    }

    @Test
    public void testReceiptScreenshotDetection() {
        String ocrText = "Tax Invoice Paid via UPI Amount Rs 1250 Transaction Summary GSTIN 27AAAC1234";
        String fileName = "Screenshot_20260920.png";
        String mimeType = "image/png";

        DetectionResult detection = DocumentDetector.detect(ocrText, fileName, mimeType);
        assertEquals(DocumentDetectionState.DOCUMENT, detection.getState());
        assertTrue(detection.isDocument());

        ClassificationResult classification = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertTrue(classification.isDocument());
        assertEquals("Finance", classification.getCategory());
        assertEquals("Tax Invoice", classification.getDocumentType());
    }

    @Test
    public void testRandomNamedAadhaarJpgDetection() {
        String ocrText = "Government of India Unique Identification Authority Aadhaar Male DOB: 10/10/2000";
        String fileName = "WhatsApp_Image_2026-09-21.jpg";
        String mimeType = "image/jpeg";

        DetectionResult detection = DocumentDetector.detect(ocrText, fileName, mimeType);
        assertEquals(DocumentDetectionState.DOCUMENT, detection.getState());
        assertTrue(detection.isDocument());

        ClassificationResult classification = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertTrue(classification.isDocument());
        assertEquals("Identity", classification.getCategory());
        assertEquals("Aadhaar Card", classification.getDocumentType());
    }

    @Test
    public void testHallTicketDetection() {
        String ocrText = "State University Examination Admit Card Hall Ticket Roll No 88102 Student Name Semester";
        String fileName = "Doc_89211.png";
        String mimeType = "image/png";

        ClassificationResult classification = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertTrue(classification.isDocument());
        assertEquals("College", classification.getCategory());
        assertEquals("Hall Ticket", classification.getDocumentType());
    }

    @Test
    public void testUnknownDocumentToOtherDocuments() {
        String ocrText = "Public Notice Official Announcement Reference Code 99201 Terms and Conditions Apply Section 12";
        String fileName = "Document_Scan.jpg";
        String mimeType = "image/jpeg";

        ClassificationResult classification = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertTrue(classification.isDocument());
        assertEquals("Other Documents", classification.getCategory());
    }
}
