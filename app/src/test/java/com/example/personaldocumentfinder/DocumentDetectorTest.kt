package com.example.personaldocumentfinder

import com.example.personaldocumentfinder.domain.DocumentClassifier
import com.example.personaldocumentfinder.domain.DocumentDetectionState
import com.example.personaldocumentfinder.domain.DocumentDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentDetectorTest {

    @Test
    fun testNormalPhotoRejection() {
        val ocrText = ""
        val fileName = "IMG_20260924_102030.jpg"
        val mimeType = "image/jpeg"

        val detection = DocumentDetector.detect(ocrText, fileName, mimeType)
        assertEquals(DocumentDetectionState.NON_DOCUMENT, detection.state)
        assertFalse(detection.isDocument)

        val classification = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertFalse(classification.isDocument)
    }

    @Test
    fun testSingleIncidentalWordPhotoRejection() {
        // Photo of a cereal box or signboard with just the word "bill" or "tax"
        val ocrText = "super bill"
        val fileName = "IMG_9912.jpg"
        val mimeType = "image/jpeg"

        val detection = DocumentDetector.detect(ocrText, fileName, mimeType)
        assertEquals(DocumentDetectionState.NON_DOCUMENT, detection.state)
        assertFalse(detection.isDocument)

        val classification = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertFalse(classification.isDocument)
    }

    @Test
    fun testReceiptScreenshotDetection() {
        val ocrText = "Tax Invoice Paid via UPI Amount Rs 1250 Transaction Summary GSTIN 27AAAC1234"
        val fileName = "Screenshot_20260920.png"
        val mimeType = "image/png"

        val detection = DocumentDetector.detect(ocrText, fileName, mimeType)
        assertEquals(DocumentDetectionState.DOCUMENT, detection.state)
        assertTrue(detection.isDocument)

        val classification = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertTrue(classification.isDocument)
        assertEquals("Finance", classification.category)
        assertEquals("Tax Invoice", classification.documentType)
    }

    @Test
    fun testRandomNamedAadhaarJpgDetection() {
        val ocrText = "Government of India Unique Identification Authority Aadhaar Male DOB: 10/10/2000"
        val fileName = "WhatsApp_Image_2026-09-21.jpg"
        val mimeType = "image/jpeg"

        val detection = DocumentDetector.detect(ocrText, fileName, mimeType)
        assertEquals(DocumentDetectionState.DOCUMENT, detection.state)
        assertTrue(detection.isDocument)

        val classification = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertTrue(classification.isDocument)
        assertEquals("Identity", classification.category)
        assertEquals("Aadhaar Card", classification.documentType)
    }

    @Test
    fun testHallTicketDetection() {
        val ocrText = "State University Examination Admit Card Hall Ticket Roll No 88102 Student Name Semester"
        val fileName = "Doc_89211.png"
        val mimeType = "image/png"

        val classification = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertTrue(classification.isDocument)
        assertEquals("College", classification.category)
        assertEquals("Hall Ticket", classification.documentType)
    }

    @Test
    fun testUnknownDocumentToOtherDocuments() {
        val ocrText = "Public Notice Official Announcement Reference Code 99201 Terms and Conditions Apply Section 12"
        val fileName = "Document_Scan.jpg"
        val mimeType = "image/jpeg"

        val classification = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertTrue(classification.isDocument)
        assertEquals("Other Documents", classification.category)
    }
}
