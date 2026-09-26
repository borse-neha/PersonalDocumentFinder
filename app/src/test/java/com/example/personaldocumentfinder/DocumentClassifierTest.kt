package com.example.personaldocumentfinder

import com.example.personaldocumentfinder.domain.DocumentClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentClassifierTest {

    @Test
    fun testAadhaarCardClassification() {
        val ocrText = "Government of India Unique Identification Authority Aadhaar DOB: 12/05/1998 Gender: Male"
        val fileName = "IMG_8392.jpg"
        val mimeType = "image/jpeg"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)

        assertTrue(result.isDocument)
        assertEquals("Identity", result.category)
        assertEquals("Aadhaar Card", result.documentType)
    }

    @Test
    fun testHallTicketClassification() {
        val ocrText = "University Examination Board Hall Ticket Seat No 98213 Student Roll No Semester 4"
        val fileName = "Screenshot_2026.png"
        val mimeType = "image/png"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)

        assertTrue(result.isDocument)
        assertEquals("College", result.category)
        assertEquals("Hall Ticket", result.documentType)
    }

    @Test
    fun testReceiptClassification() {
        val ocrText = "Tax Invoice Receipt Amount Paid Rs 1500 Bank Account Debit Transaction Successful"
        val fileName = "Payment_Receipt.pdf"
        val mimeType = "application/pdf"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)

        assertTrue(result.isDocument)
        assertEquals("Finance", result.category)
    }

    @Test
    fun testVehicleRCClassification() {
        val ocrText = "Registration Certificate Motor Vehicle RTO Chassis No MH12AB1234 Engine No XYZ"
        val fileName = "RC_Book.jpg"
        val mimeType = "image/jpeg"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)

        assertTrue(result.isDocument)
        assertEquals("Vehicle", result.category)
        assertEquals("Vehicle RC", result.documentType)
    }

    @Test
    fun testNonDocumentFamilyPhoto() {
        val ocrText = ""
        val fileName = "IMG_00291.jpg"
        val mimeType = "image/jpeg"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)

        assertFalse(result.isDocument)
        assertEquals("Other Documents", result.category)
    }
}
