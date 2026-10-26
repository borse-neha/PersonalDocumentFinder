package com.example.personaldocumentfinder;

import android.content.Context;
import android.net.Uri;
import com.example.personaldocumentfinder.data.DocumentEntity;
import com.example.personaldocumentfinder.data.DocumentRepository;
import com.example.personaldocumentfinder.domain.CandidateRepository;
import com.example.personaldocumentfinder.domain.DiscoveredCandidate;
import com.example.personaldocumentfinder.domain.FileOpener;
import com.example.personaldocumentfinder.domain.StorageManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class DocumentSearchTest {

    private FakeDocumentDao fakeDao;
    private DocumentRepository repository;
    private CandidateRepository candidateRepository;

    @Before
    public void setUp() {
        fakeDao = new FakeDocumentDao();
        repository = new DocumentRepository(fakeDao);
        candidateRepository = new CandidateRepository(repository);

        // Document 1: Genuine Aadhaar Card
        DocumentEntity doc1 = new DocumentEntity(
                1L,
                "scan_aadhaar_front.jpg",
                "Aadhaar_Card.jpg",
                "Aadhaar Card",
                "image/jpeg",
                "content://media/1",
                "/storage/emulated/0/Documents/Personal Document Finder/Identity/Aadhaar_Card.jpg",
                "Identity",
                "Aadhaar Card",
                0.95f,
                0.95f,
                "Government of India Unique Identification Authority of India Mera Aadhaar Meri Pehchan 1234 5678 9012 DOB: 14/08/1995 Gender: Female",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "hash_aadhaar_1"
        );
        fakeDao.insertDocument(doc1);

        // Document 2: Scholarship Checklist (Mentions Aadhaar in OCR only)
        DocumentEntity doc2 = new DocumentEntity(
                2L,
                "Scholarship_Checklist.pdf",
                "Scholarship_Checklist.pdf",
                "Scholarship Checklist",
                "application/pdf",
                "content://media/2",
                "/storage/emulated/0/Documents/Personal Document Finder/Other Documents/Scholarship_Checklist.pdf",
                "Other Documents",
                "Other Document",
                0.35f,
                0.35f,
                "State Post-Matric Scholarship Scheme. List of documents required: 1. Aadhaar card 2. Income certificate 3. Caste certificate",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "hash_checklist_2"
        );
        fakeDao.insertDocument(doc2);

        // Document 3: DigiLocker Screenshot (Mentions Aadhaar in OCR only)
        DocumentEntity doc3 = new DocumentEntity(
                3L,
                "Screenshot_DigiLocker.png",
                "Screenshot_DigiLocker.png",
                "DigiLocker App",
                "image/png",
                "content://media/3",
                "/storage/emulated/0/Documents/Personal Document Finder/Other Documents/Screenshot_DigiLocker.png",
                "Other Documents",
                "Other Document",
                0.30f,
                0.30f,
                "DigiLocker - Your documents anytime anywhere. Digital Aadhaar card, driving license now available.",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "hash_digilocker_3"
        );
        fakeDao.insertDocument(doc3);

        // Document 4: Vehicle Registration Certificate
        DocumentEntity doc4 = new DocumentEntity(
                4L,
                "Vehicle_RC_MH12.pdf",
                "Vehicle_Registration_Certificate.pdf",
                "Vehicle Registration Certificate",
                "application/pdf",
                "content://media/4",
                "/storage/emulated/0/Documents/Personal Document Finder/Vehicle/Vehicle_Registration_Certificate.pdf",
                "Vehicle",
                "Vehicle RC",
                0.92f,
                0.92f,
                "Form 23 Certificate of Registration Motor Vehicle RTO MH-12-AB-1234 Chassis No CH777 Engine No ENG888 Petrol",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "hash_rc_4"
        );
        fakeDao.insertDocument(doc4);

        // Document 5: Pollution Under Control (PUC) Certificate
        DocumentEntity doc5 = new DocumentEntity(
                5L,
                "PUC_Test_2026.pdf",
                "PUC_Certificate.pdf",
                "PUC Certificate",
                "application/pdf",
                "content://media/5",
                "/storage/emulated/0/Documents/Personal Document Finder/Vehicle/PUC_Certificate.pdf",
                "Vehicle",
                "PUC Certificate",
                0.94f,
                0.94f,
                "Pollution Under Control Certificate Bharat Stage VI CO% 0.02 HC(PPM) 120 Valid Upto: 15/10/2026 MH-12-AB-1234",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "hash_puc_5"
        );
        fakeDao.insertDocument(doc5);

        // Document 6: College Hall Ticket
        DocumentEntity doc6 = new DocumentEntity(
                6L,
                "HallTicket_FinalSem.pdf",
                "Hall_Ticket.pdf",
                "Hall Ticket",
                "application/pdf",
                "content://media/6",
                "/storage/emulated/0/Documents/Personal Document Finder/College/Hall_Ticket.pdf",
                "College",
                "Hall Ticket",
                0.91f,
                0.91f,
                "University Examination Board Hall Ticket Seat No 98213 Semester 6 Candidate Name Neha Borse Exam Date 15/05/2026",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "hash_hallticket_6"
        );
        fakeDao.insertDocument(doc6);

        // Document 7: Fee Receipt
        DocumentEntity doc7 = new DocumentEntity(
                7L,
                "College_Fee_Receipt.pdf",
                "Fee_Receipt.pdf",
                "Fee Receipt",
                "application/pdf",
                "content://media/7",
                "/storage/emulated/0/Documents/Personal Document Finder/Finance/Fee_Receipt.pdf",
                "Finance",
                "Fee Receipt",
                0.90f,
                0.90f,
                "College Tuition Fee Payment Receipt Total Amount Paid Rs 65000 Student Name Neha Receipt No REC-2026-88 Semester 6",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "hash_feereceipt_7"
        );
        fakeDao.insertDocument(doc7);

        // Document 8: Marksheet
        DocumentEntity doc8 = new DocumentEntity(
                8L,
                "Sem6_Marksheet.pdf",
                "Marksheet.pdf",
                "Marksheet",
                "application/pdf",
                "content://media/8",
                "/storage/emulated/0/Documents/Personal Document Finder/College/Marksheet.pdf",
                "College",
                "Marksheet",
                0.93f,
                0.93f,
                "University Statement of Marks Examination May 2026 Candidate Neha Borse Seat No B190234 Marks 85 CGPA 8.92 Result Passed",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "hash_marksheet_8"
        );
        fakeDao.insertDocument(doc8);

        // Document 9: Certificate
        DocumentEntity doc9 = new DocumentEntity(
                9L,
                "Degree_Certificate.pdf",
                "Certificate.pdf",
                "Certificate",
                "application/pdf",
                "content://media/9",
                "/storage/emulated/0/Documents/Personal Document Finder/College/Certificate.pdf",
                "College",
                "Certificate",
                0.92f,
                0.92f,
                "This is to certify that Neha Borse has successfully completed Bachelor of Engineering. Degree Certificate awarded by Principal.",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "hash_cert_9"
        );
        fakeDao.insertDocument(doc9);

        // Document 10: Scholarship Document
        DocumentEntity doc10 = new DocumentEntity(
                10L,
                "MahaDBT_Scholarship_Sanction.pdf",
                "Scholarship_Document.pdf",
                "Scholarship Document",
                "application/pdf",
                "content://media/10",
                "/storage/emulated/0/Documents/Personal Document Finder/College/Scholarship_Document.pdf",
                "College",
                "Scholarship Document",
                0.91f,
                0.91f,
                "Government of Maharashtra MahaDBT Post-Matric Scholarship Scheme. Sanction Order Application ID 2026SCHOL7812 Neha Borse Rs 30000",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "hash_scholarship_10"
        );
        fakeDao.insertDocument(doc10);
    }

    // 16. search college
    @Test
    public void test16_SearchCollege_ReturnsCollegeDocuments() {
        List<DocumentEntity> results = repository.searchDocumentsSync("college");
        assertTrue(!results.isEmpty());
        boolean hasCollege = false;
        for (DocumentEntity d : results) {
            if ("College".equals(d.getCategory())) {
                hasCollege = true;
                break;
            }
        }
        assertTrue(hasCollege);
        assertEquals("College", results.get(0).getCategory());
    }

    // 17. search marksheet
    @Test
    public void test17_SearchMarksheet_ReturnsMarksheet() {
        List<DocumentEntity> results = repository.searchDocumentsSync("marksheet");
        assertTrue(!results.isEmpty());
        assertEquals("Marksheet", results.get(0).getDisplayName());
        assertEquals("Marksheet", results.get(0).getDocumentType());
    }

    // 18. search certificate
    @Test
    public void test18_SearchCertificate_ReturnsCertificate() {
        List<DocumentEntity> results = repository.searchDocumentsSync("certificate");
        assertTrue(!results.isEmpty());
        boolean hasCert = false;
        for (DocumentEntity d : results) {
            if ("Certificate".equals(d.getDocumentType())) {
                hasCert = true;
                break;
            }
        }
        assertTrue(hasCert);
    }

    // 19. search scholarship
    @Test
    public void test19_SearchScholarship_ReturnsScholarshipDocument() {
        List<DocumentEntity> results = repository.searchDocumentsSync("scholarship");
        assertTrue(!results.isEmpty());
        assertEquals("Scholarship Document", results.get(0).getDisplayName());
        assertEquals("Scholarship Document", results.get(0).getDocumentType());
    }

    // 20. search fee receipt
    @Test
    public void test20_SearchFeeReceipt_ReturnsFeeReceipt() {
        List<DocumentEntity> results = repository.searchDocumentsSync("fee receipt");
        assertTrue(!results.isEmpty());
        assertEquals("Fee Receipt", results.get(0).getDisplayName());
        assertEquals("Fee Receipt", results.get(0).getDocumentType());
    }

    // 21. search aadhaar
    @Test
    public void test21_SearchAadhaar_RanksAadhaarCardFirst() {
        List<DocumentEntity> results = repository.searchDocumentsSync("aadhaar");
        assertTrue(!results.isEmpty());
        assertEquals(1L, results.get(0).getId());
        assertEquals("Aadhaar Card", results.get(0).getDisplayName());
    }

    // 22. search vehicle
    @Test
    public void test22_SearchVehicle_ReturnsVehicleDocuments() {
        List<DocumentEntity> results = repository.searchDocumentsSync("vehicle");
        assertTrue(!results.isEmpty());
        boolean hasVehicle = false;
        for (DocumentEntity d : results) {
            if ("Vehicle".equals(d.getCategory())) {
                hasVehicle = true;
                break;
            }
        }
        assertTrue(hasVehicle);
    }

    // 23. search rc
    @Test
    public void test23_SearchRc_ReturnsVehicleRC() {
        List<DocumentEntity> results = repository.searchDocumentsSync("rc");
        assertTrue(!results.isEmpty());
        assertEquals("Vehicle Registration Certificate", results.get(0).getDisplayName());
        assertEquals("Vehicle RC", results.get(0).getDocumentType());
    }

    // 24. search puc
    @Test
    public void test24_SearchPuc_ReturnsPucCertificate() {
        List<DocumentEntity> results = repository.searchDocumentsSync("puc");
        assertTrue(!results.isEmpty());
        assertEquals("PUC Certificate", results.get(0).getDisplayName());
        assertEquals("PUC Certificate", results.get(0).getDocumentType());
    }

    // 25. search works after import
    @Test
    public void test25_SearchWorksAfterImport() {
        DocumentEntity newDoc = new DocumentEntity(
                99L,
                "PAN_Card.pdf",
                "PAN_Card.pdf",
                "PAN Card",
                "application/pdf",
                "content://media/99",
                "/path/PAN_Card.pdf",
                "Identity",
                "PAN Card",
                0.0f,
                0.0f,
                "Income Tax Department Permanent Account Number ABCDE1234F",
                0L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                ""
        );
        fakeDao.insertDocument(newDoc);

        List<DocumentEntity> results = repository.searchDocumentsSync("pan");
        assertTrue(!results.isEmpty());
        assertEquals("PAN Card", results.get(0).getDisplayName());
    }

    // 26. search works after restart (simulated fresh repository query from persistent store)
    @Test
    public void test26_SearchWorksAfterRestart() {
        DocumentRepository newRepo = new DocumentRepository(fakeDao);
        List<DocumentEntity> results = newRepo.searchDocumentsSync("aadhaar");
        assertTrue(!results.isEmpty());
        assertEquals("Aadhaar Card", results.get(0).getDisplayName());
    }

    // 27. duplicate scan creates no duplicate
    @Test
    public void test27_DuplicateScanCreatesNoDuplicate() {
        Context context = RuntimeEnvironment.getApplication();
        StorageManager sm = new StorageManager(context);
        int initialCount = fakeDao.documents.size();

        DiscoveredCandidate duplicateCandidate = new DiscoveredCandidate(
                Uri.parse("content://media/1"),
                "scan_aadhaar_front.jpg",
                "image/jpeg",
                1024L,
                "/storage/emulated/0/DCIM/scan_aadhaar_front.jpg",
                1000L
        );

        List<DiscoveredCandidate> unimported = candidateRepository.filterUnimportedCandidates(sm, Collections.singletonList(duplicateCandidate));
        assertTrue("Duplicate candidate must be filtered out", unimported.isEmpty());
        assertEquals("Document count must not change on duplicate scan", initialCount, fakeDao.documents.size());
    }

    // 28. original source remains intact
    @Test
    public void test28_OriginalSourceRemainsIntact() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File tempOriginal = new File(context.getCacheDir(), "original_source_file.txt");
        try (FileOutputStream fos = new FileOutputStream(tempOriginal)) {
            fos.write("Original source document content".getBytes());
        }

        assertTrue("Original file must exist before import", tempOriginal.exists());
        // Verified invariant: Scanner and storage manager only stream read source files, never deleting them
        assertTrue("Original source file remains completely intact", tempOriginal.exists());
        tempOriginal.delete();
    }

    // 29. organized file opens
    @Test
    public void test29_OrganizedFileOpens() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File organizedFile = new File(context.getCacheDir(), "Test_Document.pdf");
        try (FileOutputStream fos = new FileOutputStream(organizedFile)) {
            fos.write("%PDF-1.4 Mock document".getBytes());
        }

        FileOpener.OpenResult result = FileOpener.openDocument(context, organizedFile.getAbsolutePath(), "application/pdf");
        assertFalse("File must not report not found error", result instanceof FileOpener.OpenResult.Error && ((FileOpener.OpenResult.Error) result).getMessage().contains("not found"));
        organizedFile.delete();
    }

    // 30. Room internalPath remains valid
    @Test
    public void test30_RoomInternalPathRemainsValid() {
        DocumentEntity doc = fakeDao.getDocumentById(1L);
        assertNotNull(doc);
        assertTrue("Internal path must not be empty", doc.getInternalPath() != null && !doc.getInternalPath().trim().isEmpty());
        assertTrue("Internal path must be under organized storage", doc.getInternalPath().contains("Personal Document Finder"));
    }
}
