package com.example.personaldocumentfinder;

import com.example.personaldocumentfinder.data.DocumentEntity;
import com.example.personaldocumentfinder.data.DocumentRepository;
import com.example.personaldocumentfinder.domain.ClassificationResult;
import com.example.personaldocumentfinder.domain.DetectionResult;
import com.example.personaldocumentfinder.domain.DocumentClassifier;
import com.example.personaldocumentfinder.domain.DocumentDetector;
import com.example.personaldocumentfinder.domain.PreOcrFilter;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DocumentClassificationEvidenceTest {

    private FakeDocumentDao fakeDao;
    private DocumentRepository repository;

    @Before
    public void setUp() {
        fakeDao = new FakeDocumentDao();
        repository = new DocumentRepository(fakeDao);
    }

    // 1. IMG_8392.jpg certificate is eligible for document analysis
    @Test
    public void test01_CameraImageCertificate_IsEligibleInPreOcrFilter() {
        boolean eligible = PreOcrFilter.isPlausibleCandidate(
                "IMG_8392.jpg",
                "image/jpeg",
                450 * 1024L,
                "/storage/emulated/0/DCIM/Camera/IMG_8392.jpg"
        );
        assertTrue("Camera image certificate IMG_8392.jpg must pass pre-OCR filter", eligible);
    }

    // 2. IMG_8393.jpg scholarship document is eligible
    @Test
    public void test02_CameraImageScholarship_IsEligibleInPreOcrFilter() {
        boolean eligible = PreOcrFilter.isPlausibleCandidate(
                "IMG_8393.jpg",
                "image/jpeg",
                512 * 1024L,
                "/storage/emulated/0/DCIM/Camera/IMG_8393.jpg"
        );
        assertTrue("Camera image scholarship IMG_8393.jpg must pass pre-OCR filter", eligible);
    }

    // 3. UI/UX mockup containing "10th Marksheet" is NOT Marksheet
    @Test
    public void test03_UiUxMockupContainingMarksheet_IsNotMarksheet() {
        String ocrText = "Figma UI/UX Design Mockup Splash Screen 10th Marksheet SEM 3 Results Tab Bar Search Bar Profile Empty State";
        String fileName = "IMG-20260921-WA0024.jpg";
        String mimeType = "image/jpeg";

        DetectionResult detection = DocumentDetector.detect(ocrText, fileName, mimeType);
        assertFalse(detection.isDocument());

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertNotEquals("Marksheet", result.getDocumentType());
        assertNotEquals("College", result.getCategory());
        assertFalse(result.isDocument());
    }

    // 4. Backlog examination notice containing fee/receipt/amount is NOT Fee Receipt
    @Test
    public void test04_BacklogExaminationNotice_IsNotFeeReceipt() {
        String ocrText = "Examination Cell Circular: Students are hereby informed to pay the examination fee for backlog subjects. Amount: Rs 1500 per semester. Last date to pay fee receipt submission is 25th Oct. Copy to: Registrar, Dean, Director.";
        String fileName = "IMG-20261002-WA0009.jpg";
        String mimeType = "image/jpeg";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertNotEquals("Fee Receipt", result.getDocumentType());
        assertNotEquals("Finance", result.getCategory());
        assertEquals("College", result.getCategory());
        assertEquals("College Notice", result.getDocumentType());
    }

    // 5. Genuine Marksheet is Marksheet
    @Test
    public void test05_GenuineMarksheet_ClassifiesAsCollegeMarksheet() {
        String ocrText = "Savitribai Phule Pune University Statement of Marks Examination May 2026 Candidate Name: Neha Borse Seat No: B190234 Subject: Computer Networks Marks: 85 Grade: A+ Total Marks: 650 CGPA: 8.92 Result: Passed";
        String fileName = "IMG_8821.jpg";
        String mimeType = "image/jpeg";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertTrue(result.isDocument());
        assertEquals("College", result.getCategory());
        assertEquals("Marksheet", result.getDocumentType());
        assertTrue(result.getCategoryConfidence() >= 0.70f);
        assertTrue(result.getDocumentTypeConfidence() >= 0.70f);
    }

    // 6. Genuine Fee Receipt is Fee Receipt
    @Test
    public void test06_GenuineFeeReceipt_ClassifiesAsFinanceFeeReceipt() {
        String ocrText = "College Tuition Fee Payment Receipt Receipt No: REC-2026-9042 Transaction ID: TXN881290 Amount Paid: Rs 45,000 Received with thanks from Neha Borse Admission Semester 6";
        String fileName = "IMG_3312.jpg";
        String mimeType = "image/jpeg";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertTrue(result.isDocument());
        assertEquals("Finance", result.getCategory());
        assertEquals("Fee Receipt", result.getDocumentType());
        assertTrue(result.getCategoryConfidence() >= 0.70f);
        assertTrue(result.getDocumentTypeConfidence() >= 0.70f);
    }

    // 7. Certificate detection
    @Test
    public void test07_CertificateDetection() {
        String collegeCertText = "This is to certify that Ms. Neha Borse has successfully completed Bachelor of Engineering in Computer Technology. Degree Certificate awarded by University Director and Principal.";
        ClassificationResult resultCollege = DocumentClassifier.classify(collegeCertText, "IMG_8392.jpg", "image/jpeg");
        assertTrue(resultCollege.isDocument());
        assertEquals("College", resultCollege.getCategory());
        assertEquals("Certificate", resultCollege.getDocumentType());
        assertTrue(resultCollege.getCategoryConfidence() >= 0.70f);

        String generalCertText = "Certificate of Appreciation presented to Neha Borse in recognition of valuable contribution to Rotary Community Project. Authorized Signatory President.";
        ClassificationResult resultGeneral = DocumentClassifier.classify(generalCertText, "Cert_Appreciation.pdf", "application/pdf");
        assertTrue(resultGeneral.isDocument());
        assertEquals("Personal", resultGeneral.getCategory());
        assertEquals("Certificate", resultGeneral.getDocumentType());
        assertTrue(resultGeneral.getCategoryConfidence() >= 0.70f);
    }

    // 8. Scholarship detection
    @Test
    public void test08_ScholarshipDetection() {
        String ocrText = "Government of Maharashtra MahaDBT Post-Matric Scholarship Scheme. Sanction Order Application ID: 2026SCHOL7812. Beneficiary Student: Neha Borse, Financial Assistance Sanctioned: Rs 30,000 for Academic Year 2025-2026. Department of Higher Education.";
        String fileName = "IMG_8393.jpg";
        String mimeType = "image/jpeg";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertTrue(result.isDocument());
        assertEquals("College", result.getCategory());
        assertEquals("Scholarship Document", result.getDocumentType());
        assertTrue(result.getCategoryConfidence() >= 0.70f);
        assertTrue(result.getDocumentTypeConfidence() >= 0.70f);
    }

    // 9. Aadhaar checklist is NOT Aadhaar
    @Test
    public void test09_AadhaarMentionInChecklist_NotAadhaar() {
        String ocrText = "State Post-Matric Scholarship Scheme. List of documents required: 1. Aadhaar card 2. Income certificate 3. Caste certificate 4. Death certificate of father";
        String fileName = "Scholarship_Checklist.pdf";
        String mimeType = "application/pdf";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertNotEquals("Aadhaar Card", result.getDocumentType());
        assertNotEquals("Identity", result.getCategory());
    }

    // 10. DigiLocker screenshot is NOT Aadhaar
    @Test
    public void test10_DigiLockerPromotionalText_NotAadhaar() {
        String ocrText = "DigiLocker - Your documents anytime anywhere. Digital Aadhaar card, driving license now available on DigiLocker.";
        String fileName = "Screenshot_DigiLocker.png";
        String mimeType = "image/png";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertNotEquals("Aadhaar Card", result.getDocumentType());
        assertNotEquals("Identity", result.getCategory());
    }

    // 11. Notebook is NOT Vehicle
    @Test
    public void test11_Notebook_NotVehicle() {
        String ocrText = "Chapter 4 Mathematics Exercise 4.2 Problem 3 Solution: x = 24.5, y = 18.2, calculate total = 42.7 Ans: 42.7 registration no 12";
        String fileName = "IMG_homework_math.jpg";
        String mimeType = "image/jpeg";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertNotEquals("Vehicle", result.getCategory());
        assertNotEquals("Vehicle RC", result.getDocumentType());
        assertNotEquals("PUC Certificate", result.getDocumentType());
    }

    // 12. Academic registration document is NOT Vehicle
    @Test
    public void test12_AcademicRegistration_NotVehicleRC() {
        String ocrText = "University Student Registration Form Academic Year 2026. Student Registration No: 8812. Semester 4 Admission Course BTech Computer Engineering.";
        String fileName = "Student_Registration.pdf";
        String mimeType = "application/pdf";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertNotEquals("Vehicle", result.getCategory());
        assertNotEquals("Vehicle RC", result.getDocumentType());
    }

    // 13. Pre-University College PUC is NOT Vehicle PUC
    @Test
    public void test13_PreUniversityCollegePUC_NotVehiclePUC() {
        String ocrText = "Karnataka State Board Pre-University Course PUC 2nd Year Mathematics Syllabus Chapter 3 Derivative dy/dx Marks: 100 Date: 12/04/2026";
        String fileName = "PUC_Math_Syllabus.pdf";
        String mimeType = "application/pdf";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertNotEquals("PUC Certificate", result.getDocumentType());
        assertNotEquals("Vehicle", result.getCategory());
    }

    // 14. Genuine PUC is Vehicle PUC
    @Test
    public void test14_RealPUC_ClassifiesAsVehiclePUC() {
        String ocrText = "Pollution Under Control Certificate PUC Test Date: 12/01/2026 Valid Up To: 11/07/2026 Emission Norms Bharat Stage VI CO% 0.02 HC(PPM) 120 MH-12-AB-1234";
        String fileName = "PUC_Certificate.pdf";
        String mimeType = "application/pdf";

        ClassificationResult result = DocumentClassifier.classify(ocrText, fileName, mimeType);
        assertTrue(result.isDocument());
        assertEquals("Vehicle", result.getCategory());
        assertEquals("PUC Certificate", result.getDocumentType());
        assertTrue(result.getCategoryConfidence() >= 0.70f);
        assertTrue(result.getDocumentTypeConfidence() >= 0.70f);
    }

    // 15. Blank displayName gets healed
    @Test
    public void test15_BlankDisplayNameGetsHealed() {
        String healedName = repository.generateUniqueDisplayName("Marksheet", "College");
        assertEquals("Marksheet", healedName);

        DocumentEntity existingDoc = new DocumentEntity(
                1L,
                "IMG_9999.jpg",
                "Marksheet.jpg",
                "", // Blank displayName in DB
                "image/jpeg",
                "content://media/99",
                "/path/to/Marksheet.jpg",
                "College",
                "Marksheet",
                0.0f,
                0.0f,
                "",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                ""
        );
        // Verify effectiveDisplayName falls back to documentType when blank
        assertEquals("Marksheet", existingDoc.getEffectiveDisplayName());

        // Generating next name when existing doc is inserted
        DocumentEntity inserted = existingDoc.copy();
        inserted.setDisplayName("Marksheet");
        fakeDao.insertDocument(inserted);

        String secondHealed = repository.generateUniqueDisplayName("Marksheet", "College");
        assertEquals("Marksheet (2)", secondHealed);
    }
}
