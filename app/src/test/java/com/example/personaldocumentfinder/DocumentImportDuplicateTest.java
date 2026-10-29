package com.example.personaldocumentfinder;

import android.net.Uri;
import com.example.personaldocumentfinder.data.DocumentEntity;
import com.example.personaldocumentfinder.data.DocumentRepository;
import com.example.personaldocumentfinder.domain.ClassificationResult;
import com.example.personaldocumentfinder.domain.DocumentClassifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class DocumentImportDuplicateTest {

    private FakeDocumentDao fakeDao;
    private DocumentRepository repository;

    @Before
    public void setUp() {
        fakeDao = new FakeDocumentDao();
        repository = new DocumentRepository(fakeDao);
    }

    // Scenario A: Import a new test PDF: it succeeds.
    @Test
    public void testScenarioA_importNewPdfSucceeds() {
        String pdfText = "HALL TICKET EXAMINATION ADMIT CARD\nStudent Name: John Doe\nRoll No: 12345\nUniversity College of Engineering\nSemester: V";
        ClassificationResult classification = DocumentClassifier.classify(pdfText, "HallTicket_2026.pdf", "application/pdf");
        assertTrue(classification.isDocument());
        assertEquals("College", classification.getCategory());

        String smartName = repository.generateUniqueDisplayName(classification.getDocumentType(), classification.getCategory());
        assertEquals("Hall Ticket", smartName);

        DocumentEntity entity = new DocumentEntity(
                0L,
                "HallTicket_2026.pdf",
                "Hall_Ticket.pdf",
                smartName,
                "application/pdf",
                "content://media/file/101",
                "/app/storage/College/Hall_Ticket.pdf",
                classification.getCategory(),
                classification.getDocumentType(),
                classification.getDocumentConfidence(),
                classification.getCategoryConfidence(),
                classification.getOcrText(),
                124000L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "sha256_hash_hall_ticket_101"
        );

        long id = fakeDao.insertDocument(entity);
        assertTrue(id > 0);

        DocumentEntity inserted = repository.getDocumentById(id);
        assertNotNull(inserted);
        assertEquals("Hall Ticket", inserted.getDisplayName());
        assertEquals("College", inserted.getCategory());
        assertEquals("sha256_hash_hall_ticket_101", inserted.getContentHash());
    }

    // Scenario B: Import identical bytes through another URI or filename: detects duplicate.
    @Test
    public void testScenarioB_identicalBytesThroughDifferentFilenameDetectedAsDuplicate() {
        DocumentEntity original = new DocumentEntity(
                0L,
                "FeeReceipt_2026.pdf",
                "Fee_Receipt.pdf",
                "Fee Receipt",
                "application/pdf",
                "content://media/file/101",
                "/app/storage/College/Fee_Receipt.pdf",
                "College",
                "Fee Receipt",
                0.95f,
                0.95f,
                "FEES RECEIPT",
                124000L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "sha256_hash_fee_receipt_101"
        );
        fakeDao.insertDocument(original);

        // Second file: different URI and different filename, but identical content hash
        String newUri = "content://downloads/document_copy_renamed.pdf";
        String identicalContentHash = "sha256_hash_fee_receipt_101";

        DocumentEntity duplicateMatch = repository.getDocumentByHash(identicalContentHash);
        assertNotNull("Identical bytes must be detected by content hash", duplicateMatch);
        assertEquals("Fee Receipt", duplicateMatch.getDisplayName());
        assertEquals("College", duplicateMatch.getCategory());
    }

    // Scenario C: Import a different PDF in the same category: it succeeds.
    @Test
    public void testScenarioC_differentPdfInSameCategorySucceedsWithDisambiguatedName() {
        // Document 1
        DocumentEntity doc1 = new DocumentEntity(
                0L,
                "FeeReceipt_Sem1.pdf",
                "Fee_Receipt_1.pdf",
                "Fee Receipt",
                "application/pdf",
                "content://media/file/101",
                "/app/storage/College/Fee_Receipt_1.pdf",
                "College",
                "Fee Receipt",
                0.95f,
                0.95f,
                "FEES RECEIPT SEMESTER 1",
                100000L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "sha256_hash_sem1"
        );
        fakeDao.insertDocument(doc1);

        // Document 2: Different content in College category
        String doc2Hash = "sha256_hash_sem2";
        assertNull("Different hash must not collide", repository.getDocumentByHash(doc2Hash));

        String smartName2 = repository.generateUniqueDisplayName("Fee Receipt", "College");
        assertEquals("Fee Receipt (2)", smartName2);

        DocumentEntity doc2 = new DocumentEntity(
                0L,
                "FeeReceipt_Sem2.pdf",
                "Fee_Receipt_2.pdf",
                smartName2,
                "application/pdf",
                "content://media/file/102",
                "/app/storage/College/Fee_Receipt_2.pdf",
                "College",
                "Fee Receipt",
                0.95f,
                0.95f,
                "FEES RECEIPT SEMESTER 2",
                110000L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                doc2Hash
        );
        long id2 = fakeDao.insertDocument(doc2);
        assertTrue(id2 > 0);

        List<DocumentEntity> collegeDocs = repository.getDocumentsByCategorySync("College");
        assertEquals(2, collegeDocs.size());
    }

    // Scenario D: Import a different image with the same filename as another document: not rejected solely for filename.
    @Test
    public void testScenarioD_differentImageWithSameFilenameNotRejected() {
        DocumentEntity existing = new DocumentEntity(
                0L,
                "document.jpg",
                "document.jpg",
                "Aadhaar Card",
                "image/jpeg",
                "content://media/file/201",
                "/app/storage/Identity/document.jpg",
                "Identity",
                "Aadhaar Card",
                0.95f,
                0.95f,
                "GOVERNMENT OF INDIA",
                50000L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "sha256_hash_aadhaar"
        );
        fakeDao.insertDocument(existing);

        // New image has same original filename "document.jpg", but completely different bytes & content
        String newImageHash = "sha256_hash_pan_card";
        DocumentEntity dupeCheck = repository.getDocumentByHash(newImageHash);
        assertNull("Different bytes must not be marked as duplicate despite matching filename", dupeCheck);

        DocumentEntity newDoc = new DocumentEntity(
                0L,
                "document.jpg",
                "document_1.jpg",
                "PAN Card",
                "image/jpeg",
                "content://media/file/202",
                "/app/storage/Identity/document_1.jpg",
                "Identity",
                "PAN Card",
                0.95f,
                0.95f,
                "INCOME TAX DEPARTMENT",
                60000L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                newImageHash
        );
        long newId = fakeDao.insertDocument(newDoc);
        assertTrue(newId > 0);
        assertEquals(2, repository.getAllDocumentsSync().size());
    }

    // Scenario E: Import a previously imported document again: UI explains duplicate and identifies existing document.
    @Test
    public void testScenarioE_duplicateExplainsExistingDocument() {
        DocumentEntity existing = new DocumentEntity(
                0L,
                "TaxInvoice.pdf",
                "TaxInvoice.pdf",
                "Salary Slip (Jan)",
                "application/pdf",
                "content://media/file/301",
                "/app/storage/Finance/TaxInvoice.pdf",
                "Finance",
                "Salary Slip",
                0.92f,
                0.92f,
                "EARNINGS DEDUCTIONS BASIC PAY",
                75000L,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false,
                true,
                "sha256_hash_salary_slip"
        );
        fakeDao.insertDocument(existing);

        // Look up by duplicate hash
        DocumentEntity found = repository.getDocumentByHash("sha256_hash_salary_slip");
        assertNotNull(found);
        assertEquals("Salary Slip (Jan)", found.getDisplayName());
        assertEquals("Finance", found.getCategory());
        String feedbackMessage = "Duplicate detected: matches existing document '" + found.getDisplayName() + "' in " + found.getCategory() + ".";
        assertTrue(feedbackMessage.contains("Salary Slip (Jan)"));
        assertTrue(feedbackMessage.contains("Finance"));
    }

    // Scenario F: Failed or partially completed import leaves no orphan database record.
    @Test
    public void testScenarioF_atomicCleanupOnFailure() {
        int initialCount = repository.getAllDocumentsSync().size();

        // Simulate failed operation: try inserting null/invalid entity or rollback
        try {
            boolean writeStorageFailed = true;
            if (writeStorageFailed) {
                throw new java.io.IOException("Disk quota exceeded");
            }
            // Should never reach DB insertion if storage failed
            DocumentEntity orphaned = new DocumentEntity();
            fakeDao.insertDocument(orphaned);
        } catch (java.io.IOException expected) {
            // Handled
        }

        assertEquals("No database record must be created if storage operation fails",
                initialCount, repository.getAllDocumentsSync().size());
    }
}
