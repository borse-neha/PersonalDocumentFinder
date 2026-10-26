package com.example.personaldocumentfinder;

import com.example.personaldocumentfinder.domain.ClassificationResult;
import com.example.personaldocumentfinder.domain.DocumentClassifier;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DocumentClassifierTest {

    @Test
    public void testAadhaarCardClassification() {
        String ocrText = "Government of India Unique Identification Authority Aadhaar DOB: 12/05/1998 Gender: Male";
        String fileName = "IMG_8392.jpg";
        String mimeType = "image/jpeg";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);

        assertTrue(result.isDocument());
        assertEquals("Identity", result.getCategory());
        assertEquals("Aadhaar Card", result.getDocumentType());
    }

    @Test
    public void testHallTicketClassification() {
        String ocrText = "University Examination Board Hall Ticket Seat No 98213 Student Roll No Semester 4";
        String fileName = "Screenshot_2026.png";
        String mimeType = "image/png";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);

        assertTrue(result.isDocument());
        assertEquals("College", result.getCategory());
        assertEquals("Hall Ticket", result.getDocumentType());
    }

    @Test
    public void testReceiptClassification() {
        String ocrText = "Tax Invoice Receipt Amount Paid Rs 1500 Bank Account Debit Transaction Successful";
        String fileName = "Payment_Receipt.pdf";
        String mimeType = "application/pdf";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);

        assertTrue(result.isDocument());
        assertEquals("Finance", result.getCategory());
    }

    @Test
    public void testVehicleRCClassification() {
        String ocrText = "Registration Certificate Motor Vehicle RTO Chassis No MH12AB1234 Engine No XYZ";
        String fileName = "RC_Book.jpg";
        String mimeType = "image/jpeg";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);

        assertTrue(result.isDocument());
        assertEquals("Vehicle", result.getCategory());
        assertEquals("Vehicle RC", result.getDocumentType());
    }

    @Test
    public void testNonDocumentFamilyPhoto() {
        String ocrText = "";
        String fileName = "IMG_00291.jpg";
        String mimeType = "image/jpeg";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);

        assertFalse(result.isDocument());
        assertEquals("Other Documents", result.getCategory());
    }
}
