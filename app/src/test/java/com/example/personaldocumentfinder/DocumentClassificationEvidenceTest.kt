package com.example.personaldocumentfinder

import com.example.personaldocumentfinder.data.DocumentEntity
import com.example.personaldocumentfinder.data.DocumentRepository
import com.example.personaldocumentfinder.domain.DocumentClassifier
import com.example.personaldocumentfinder.domain.DocumentDetectionState
import com.example.personaldocumentfinder.domain.DocumentDetector
import com.example.personaldocumentfinder.domain.PreOcrFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DocumentClassificationEvidenceTest {

    private lateinit var fakeDao: FakeDocumentDao
    private lateinit var repository: DocumentRepository

    @Before
    fun setUp() {
        fakeDao = FakeDocumentDao()
        repository = DocumentRepository(fakeDao)
    }

    // 1. IMG_8392.jpg certificate is eligible for document analysis
    @Test
    fun test01_CameraImageCertificate_IsEligibleInPreOcrFilter() {
        val eligible = PreOcrFilter.isPlausibleCandidate(
            name = "IMG_8392.jpg",
            mimeType = "image/jpeg",
            fileSize = 450 * 1024L,
            filePath = "/storage/emulated/0/DCIM/Camera/IMG_8392.jpg"
        )
        assertTrue("Camera image certificate IMG_8392.jpg must pass pre-OCR filter", eligible)
    }

    // 2. IMG_8393.jpg scholarship document is eligible
    @Test
    fun test02_CameraImageScholarship_IsEligibleInPreOcrFilter() {
        val eligible = PreOcrFilter.isPlausibleCandidate(
            name = "IMG_8393.jpg",
            mimeType = "image/jpeg",
            fileSize = 512 * 1024L,
            filePath = "/storage/emulated/0/DCIM/Camera/IMG_8393.jpg"
        )
        assertTrue("Camera image scholarship IMG_8393.jpg must pass pre-OCR filter", eligible)
    }

    // 3. UI/UX mockup containing "10th Marksheet" is NOT Marksheet
    @Test
    fun test03_UiUxMockupContainingMarksheet_IsNotMarksheet() {
        val ocrText = "Figma UI/UX Design Mockup Splash Screen 10th Marksheet SEM 3 Results Tab Bar Search Bar Profile Empty State"
        val fileName = "IMG-20260921-WA0024.jpg"
        val mimeType = "image/jpeg"

        val detection = DocumentDetector.detect(ocrText, fileName, mimeType)
        assertFalse(detection.isDocument)

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertNotEquals("Marksheet", result.documentType)
        assertNotEquals("College", result.category)
        assertFalse(result.isDocument)
    }

    // 4. Backlog examination notice containing fee/receipt/amount is NOT Fee Receipt
    @Test
    fun test04_BacklogExaminationNotice_IsNotFeeReceipt() {
        val ocrText = "Examination Cell Circular: Students are hereby informed to pay the examination fee for backlog subjects. Amount: Rs 1500 per semester. Last date to pay fee receipt submission is 25th Oct. Copy to: Registrar, Dean, Director."
        val fileName = "IMG-20261002-WA0009.jpg"
        val mimeType = "image/jpeg"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertNotEquals("Fee Receipt", result.documentType)
        assertNotEquals("Finance", result.category)
        assertEquals("College", result.category)
        assertEquals("College Notice", result.documentType)
    }

    // 5. Genuine Marksheet is Marksheet
    @Test
    fun test05_GenuineMarksheet_ClassifiesAsCollegeMarksheet() {
        val ocrText = "Savitribai Phule Pune University Statement of Marks Examination May 2026 Candidate Name: Neha Borse Seat No: B190234 Subject: Computer Networks Marks: 85 Grade: A+ Total Marks: 650 CGPA: 8.92 Result: Passed"
        val fileName = "IMG_8821.jpg"
        val mimeType = "image/jpeg"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertTrue(result.isDocument)
        assertEquals("College", result.category)
        assertEquals("Marksheet", result.documentType)
        assertTrue(result.categoryConfidence >= 0.70f)
        assertTrue(result.documentTypeConfidence >= 0.70f)
    }

    // 6. Genuine Fee Receipt is Fee Receipt
    @Test
    fun test06_GenuineFeeReceipt_ClassifiesAsFinanceFeeReceipt() {
        val ocrText = "College Tuition Fee Payment Receipt Receipt No: REC-2026-9042 Transaction ID: TXN881290 Amount Paid: Rs 45,000 Received with thanks from Neha Borse Admission Semester 6"
        val fileName = "IMG_3312.jpg"
        val mimeType = "image/jpeg"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertTrue(result.isDocument)
        assertEquals("Finance", result.category)
        assertEquals("Fee Receipt", result.documentType)
        assertTrue(result.categoryConfidence >= 0.70f)
        assertTrue(result.documentTypeConfidence >= 0.70f)
    }

    // 7. Certificate detection
    @Test
    fun test07_CertificateDetection() {
        val collegeCertText = "This is to certify that Ms. Neha Borse has successfully completed Bachelor of Engineering in Computer Technology. Degree Certificate awarded by University Director and Principal."
        val resultCollege = DocumentClassifier.classify(collegeCertText, "IMG_8392.jpg", "image/jpeg")
        assertTrue(resultCollege.isDocument)
        assertEquals("College", resultCollege.category)
        assertEquals("Certificate", resultCollege.documentType)
        assertTrue(resultCollege.categoryConfidence >= 0.70f)

        val generalCertText = "Certificate of Appreciation presented to Neha Borse in recognition of valuable contribution to Rotary Community Project. Authorized Signatory President."
        val resultGeneral = DocumentClassifier.classify(generalCertText, "Cert_Appreciation.pdf", "application/pdf")
        assertTrue(resultGeneral.isDocument)
        assertEquals("Personal", resultGeneral.category)
        assertEquals("Certificate", resultGeneral.documentType)
        assertTrue(resultGeneral.categoryConfidence >= 0.70f)
    }

    // 8. Scholarship detection
    @Test
    fun test08_ScholarshipDetection() {
        val ocrText = "Government of Maharashtra MahaDBT Post-Matric Scholarship Scheme. Sanction Order Application ID: 2026SCHOL7812. Beneficiary Student: Neha Borse, Financial Assistance Sanctioned: Rs 30,000 for Academic Year 2025-2026. Department of Higher Education."
        val fileName = "IMG_8393.jpg"
        val mimeType = "image/jpeg"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertTrue(result.isDocument)
        assertEquals("College", result.category)
        assertEquals("Scholarship Document", result.documentType)
        assertTrue(result.categoryConfidence >= 0.70f)
        assertTrue(result.documentTypeConfidence >= 0.70f)
    }

    // 9. Aadhaar checklist is NOT Aadhaar
    @Test
    fun test09_AadhaarMentionInChecklist_NotAadhaar() {
        val ocrText = "State Post-Matric Scholarship Scheme. List of documents required: 1. Aadhaar card 2. Income certificate 3. Caste certificate 4. Death certificate of father"
        val fileName = "Scholarship_Checklist.pdf"
        val mimeType = "application/pdf"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertNotEquals("Aadhaar Card", result.documentType)
        assertNotEquals("Identity", result.category)
    }

    // 10. DigiLocker screenshot is NOT Aadhaar
    @Test
    fun test10_DigiLockerPromotionalText_NotAadhaar() {
        val ocrText = "DigiLocker - Your documents anytime anywhere. Digital Aadhaar card, driving license now available on DigiLocker."
        val fileName = "Screenshot_DigiLocker.png"
        val mimeType = "image/png"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertNotEquals("Aadhaar Card", result.documentType)
        assertNotEquals("Identity", result.category)
    }

    // 11. Notebook is NOT Vehicle
    @Test
    fun test11_Notebook_NotVehicle() {
        val ocrText = "Chapter 4 Mathematics Exercise 4.2 Problem 3 Solution: x = 24.5, y = 18.2, calculate total = 42.7 Ans: 42.7 registration no 12"
        val fileName = "IMG_homework_math.jpg"
        val mimeType = "image/jpeg"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertNotEquals("Vehicle", result.category)
        assertNotEquals("Vehicle RC", result.documentType)
        assertNotEquals("PUC Certificate", result.documentType)
    }

    // 12. Academic registration document is NOT Vehicle
    @Test
    fun test12_AcademicRegistration_NotVehicleRC() {
        val ocrText = "University Student Registration Form Academic Year 2026. Student Registration No: 8812. Semester 4 Admission Course BTech Computer Engineering."
        val fileName = "Student_Registration.pdf"
        val mimeType = "application/pdf"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertNotEquals("Vehicle", result.category)
        assertNotEquals("Vehicle RC", result.documentType)
    }

    // 13. Pre-University College PUC is NOT Vehicle PUC
    @Test
    fun test13_PreUniversityCollegePUC_NotVehiclePUC() {
        val ocrText = "Karnataka State Board Pre-University Course PUC 2nd Year Mathematics Syllabus Chapter 3 Derivative dy/dx Marks: 100 Date: 12/04/2026"
        val fileName = "PUC_Math_Syllabus.pdf"
        val mimeType = "application/pdf"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertNotEquals("PUC Certificate", result.documentType)
        assertNotEquals("Vehicle", result.category)
    }

    // 14. Genuine PUC is Vehicle PUC
    @Test
    fun test14_RealPUC_ClassifiesAsVehiclePUC() {
        val ocrText = "Pollution Under Control Certificate PUC Test Date: 12/01/2026 Valid Up To: 11/07/2026 Emission Norms Bharat Stage VI CO% 0.02 HC(PPM) 120 MH-12-AB-1234"
        val fileName = "PUC_Certificate.pdf"
        val mimeType = "application/pdf"

        val result = DocumentClassifier.classify(ocrText, fileName, mimeType)
        assertTrue(result.isDocument)
        assertEquals("Vehicle", result.category)
        assertEquals("PUC Certificate", result.documentType)
        assertTrue(result.categoryConfidence >= 0.70f)
        assertTrue(result.documentTypeConfidence >= 0.70f)
    }

    // 15. Blank displayName gets healed
    @Test
    fun test15_BlankDisplayNameGetsHealed() = runBlocking {
        val healedName = repository.generateUniqueDisplayName("Marksheet", "College")
        assertEquals("Marksheet", healedName)

        val existingDoc = DocumentEntity(
            id = 1L,
            originalName = "IMG_9999.jpg",
            storedFileName = "Marksheet.jpg",
            displayName = "", // Blank displayName in DB
            mimeType = "image/jpeg",
            originalUri = "content://media/99",
            internalPath = "/path/to/Marksheet.jpg",
            category = "College",
            documentType = "Marksheet"
        )
        // Verify effectiveDisplayName falls back to documentType when blank
        assertEquals("Marksheet", existingDoc.effectiveDisplayName)

        // Generating next name when existing doc is inserted
        fakeDao.insertDocument(existingDoc.copy(displayName = "Marksheet"))
        val secondHealed = repository.generateUniqueDisplayName("Marksheet", "College")
        assertEquals("Marksheet (2)", secondHealed)
    }
}
