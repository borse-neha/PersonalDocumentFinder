package com.example.personaldocumentfinder;

import com.example.personaldocumentfinder.data.DocumentEntity;
import com.example.personaldocumentfinder.data.DocumentRepository;
import com.example.personaldocumentfinder.data.Result;
import com.example.personaldocumentfinder.data.Unit;
import com.example.personaldocumentfinder.domain.ClassificationResult;
import com.example.personaldocumentfinder.domain.DocumentClassifier;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DocumentNamingAndRenameTest {

    private FakeDocumentDao fakeDao;
    private DocumentRepository repository;

    @Before
    public void setUp() {
        fakeDao = new FakeDocumentDao();
        repository = new DocumentRepository(fakeDao);
    }

    @Test
    public void testClassifiedAadhaarDocumentReceivesAadhaarCard() {
        // 1. Classified Aadhaar document receives "Aadhaar Card"
        String ocrText = "Government of India Unique Identification Authority of India Aadhaar 1234 5678 9012";
        String originalFileName = "IMG_2026_09_Aadhaar.jpg";
        ClassificationResult classification = DocumentClassifier.classify(ocrText, originalFileName, "image/jpeg");

        String generatedName = repository.generateUniqueDisplayName(classification.getDocumentType(), classification.getCategory());
        assertEquals("Aadhaar Card", generatedName);
    }

    @Test
    public void testClassifiedPanDocumentReceivesPanCard() {
        // 2. Classified PAN document receives "PAN Card"
        String ocrText = "INCOME TAX DEPARTMENT GOVT OF INDIA Permanent Account Number PAN ABCDE1234F";
        String originalFileName = "scan_pan_card.pdf";
        ClassificationResult classification = DocumentClassifier.classify(ocrText, originalFileName, "application/pdf");

        String generatedName = repository.generateUniqueDisplayName(classification.getDocumentType(), classification.getCategory());
        assertEquals("PAN Card", generatedName);
    }

    @Test
    public void testMultipleSameTypeDocumentsReceiveUniqueNames() {
        // 3. Multiple same-type documents receive unique names: "Aadhaar Card", "Aadhaar Card (2)", "Aadhaar Card (3)"
        String name1 = repository.generateUniqueDisplayName("Aadhaar Card", "Identity");
        assertEquals("Aadhaar Card", name1);

        // Persist first document into Room
        DocumentEntity doc1 = new DocumentEntity(
                "IMG_001.jpg",
                "IMG_001.jpg",
                name1,
                "image/jpeg",
                "content://media/1",
                "/data/user/0/Identity/IMG_001.jpg",
                "Identity",
                "Aadhaar Card"
        );
        doc1.setId(1L);
        fakeDao.insertDocument(doc1);

        // Second Aadhaar Card
        String name2 = repository.generateUniqueDisplayName("Aadhaar Card", "Identity");
        assertEquals("Aadhaar Card (2)", name2);

        // Persist second document
        DocumentEntity doc2 = new DocumentEntity(
                "IMG_002.jpg",
                "IMG_002.jpg",
                name2,
                "image/jpeg",
                "content://media/2",
                "/data/user/0/Identity/IMG_002.jpg",
                "Identity",
                "Aadhaar Card"
        );
        doc2.setId(2L);
        fakeDao.insertDocument(doc2);

        // Third Aadhaar Card
        String name3 = repository.generateUniqueDisplayName("Aadhaar Card", "Identity");
        assertEquals("Aadhaar Card (3)", name3);

        // Likewise for Hall Ticket
        String htName1 = repository.generateUniqueDisplayName("Hall Ticket", "College");
        assertEquals("Hall Ticket", htName1);

        DocumentEntity doc3 = new DocumentEntity(
                "Doc_101.pdf",
                "Doc_101.pdf",
                htName1,
                "application/pdf",
                "content://media/3",
                "/data/user/0/College/Doc_101.pdf",
                "College",
                "Hall Ticket"
        );
        doc3.setId(3L);
        fakeDao.insertDocument(doc3);

        String htName2 = repository.generateUniqueDisplayName("Hall Ticket", "College");
        assertEquals("Hall Ticket (2)", htName2);
    }

    @Test
    public void testOriginalFilenameRemainsUnchanged() {
        // 4. Original filename remains unchanged when display name is assigned
        String originalFileName = "IMG_8392.jpg";
        String autoDisplayName = repository.generateUniqueDisplayName("Aadhaar Card", "Identity");

        DocumentEntity doc = new DocumentEntity(
                originalFileName,
                "Aadhaar_Card_1.jpg",
                autoDisplayName,
                "image/jpeg",
                "content://media/1",
                "/data/user/0/Identity/Aadhaar_Card_1.jpg",
                "Identity",
                "Aadhaar Card"
        );
        doc.setId(1L);
        fakeDao.insertDocument(doc);

        DocumentEntity retrieved = fakeDao.getDocumentById(1L);
        assertEquals("IMG_8392.jpg", retrieved != null ? retrieved.getOriginalName() : null);
        assertEquals("Aadhaar Card", retrieved != null ? retrieved.getDisplayName() : null);
    }

    @Test
    public void testUserRenamePersists() {
        // 5. User rename persists
        DocumentEntity doc = new DocumentEntity(
                "IMG_8392.jpg",
                "IMG_8392.jpg",
                "Aadhaar Card",
                "image/jpeg",
                "content://media/1",
                "/data/user/0/Identity/IMG_8392.jpg",
                "Identity",
                "Aadhaar Card"
        );
        doc.setId(1L);
        fakeDao.insertDocument(doc);

        Result<Unit> renameResult = repository.updateDisplayName(1L, "Mom's Aadhaar Card");
        assertTrue(renameResult.isSuccess());

        DocumentEntity updated = fakeDao.getDocumentById(1L);
        assertEquals("Mom's Aadhaar Card", updated != null ? updated.getDisplayName() : null);
        assertEquals("IMG_8392.jpg", updated != null ? updated.getOriginalName() : null); // originalName intact
    }

    @Test
    public void testCancelRenamePreservesPreviousDisplayName() {
        // 6. Cancel rename preserves previous displayName
        String previousName = "Fee Receipt 2026";
        DocumentEntity doc = new DocumentEntity(
                "Receipt_Spring.pdf",
                "Receipt_Spring.pdf",
                previousName,
                "application/pdf",
                "content://media/1",
                "/data/user/0/Finance/Receipt_Spring.pdf",
                "Finance",
                "Payment Receipt"
        );
        doc.setId(1L);
        fakeDao.insertDocument(doc);

        // Simulating Cancel action in UI (no repository update is executed)
        DocumentEntity loadedDoc = fakeDao.getDocumentById(1L);
        assertEquals(previousName, loadedDoc != null ? loadedDoc.getDisplayName() : null);
    }

    @Test
    public void testEmptyOrInvalidRenameIsHandledCorrectly() {
        // 7. Empty/whitespace-only and duplicate rename is handled correctly
        DocumentEntity doc1 = new DocumentEntity(
                "doc1.pdf",
                "doc1.pdf",
                "Salary Slip Jan",
                "application/pdf",
                "content://media/1",
                "/data/user/0/Office/doc1.pdf",
                "Office",
                "Salary Slip"
        );
        doc1.setId(1L);

        DocumentEntity doc2 = new DocumentEntity(
                "doc2.pdf",
                "doc2.pdf",
                "Salary Slip Feb",
                "application/pdf",
                "content://media/2",
                "/data/user/0/Office/doc2.pdf",
                "Office",
                "Salary Slip"
        );
        doc2.setId(2L);

        fakeDao.insertDocument(doc1);
        fakeDao.insertDocument(doc2);

        // Empty string
        Result<Unit> emptyResult = repository.updateDisplayName(1L, "");
        assertTrue(emptyResult.isFailure());

        // Whitespace only
        Result<Unit> whitespaceResult = repository.updateDisplayName(1L, "   ");
        assertTrue(whitespaceResult.isFailure());

        // Name exceeding 100 characters
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            sb.append('A');
        }
        Result<Unit> tooLongResult = repository.updateDisplayName(1L, sb.toString());
        assertTrue(tooLongResult.isFailure());

        // Duplicate name (trying to rename doc1 to doc2's name)
        Result<Unit> duplicateResult = repository.updateDisplayName(1L, "Salary Slip Feb");
        assertTrue(duplicateResult.isFailure());

        // Name was not changed on failed validation
        assertEquals("Salary Slip Jan", fakeDao.getDocumentById(1L).getDisplayName());
    }

    @Test
    public void testExistingDocumentsWithoutDisplayNameReceiveSafeDefault() {
        // 8. Existing documents without displayName receive a safe default via effectiveDisplayName
        DocumentEntity legacyDocWithType = new DocumentEntity(
                1L,
                "scan_001.pdf",
                "scan_001.pdf",
                "", // Empty displayName from older schema
                "application/pdf",
                "content://media/1",
                "/data/user/0/College/scan_001.pdf",
                "College",
                "Hall Ticket",
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
        assertEquals("Hall Ticket", legacyDocWithType.getEffectiveDisplayName());

        DocumentEntity legacyDocGeneric = new DocumentEntity(
                2L,
                "receipt_april.pdf",
                "receipt_april.pdf",
                "", // Empty displayName
                "application/pdf",
                "content://media/2",
                "/data/user/0/Finance/receipt_april.pdf",
                "Finance",
                "Unclassified Document",
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
        assertEquals("receipt_april.pdf", legacyDocGeneric.getEffectiveDisplayName());
    }

    @Test
    public void testRenamedDocumentDisplayNameSurvivesReloadFromRoom() {
        // 9. A renamed document's displayName survives reload from Room
        DocumentEntity doc = new DocumentEntity(
                "IMG_9921.jpg",
                "IMG_9921.jpg",
                "Vehicle Registration Certificate",
                "image/jpeg",
                "content://media/55",
                "/data/user/0/Vehicle/IMG_9921.jpg",
                "Vehicle",
                "Vehicle RC"
        );
        doc.setId(55L);
        fakeDao.insertDocument(doc);

        // Rename
        repository.updateDisplayName(55L, "My Honda Civic RC");

        // Reload from DAO
        DocumentEntity reloaded = fakeDao.getDocumentById(55L);
        assertEquals("My Honda Civic RC", reloaded != null ? reloaded.getDisplayName() : null);
        assertEquals("My Honda Civic RC", reloaded != null ? reloaded.getEffectiveDisplayName() : null);
    }
}
