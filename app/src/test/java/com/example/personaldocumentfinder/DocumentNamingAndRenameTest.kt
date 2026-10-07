package com.example.personaldocumentfinder

import com.example.personaldocumentfinder.data.DocumentEntity
import com.example.personaldocumentfinder.data.DocumentRepository
import com.example.personaldocumentfinder.domain.DocumentClassifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DocumentNamingAndRenameTest {

    private lateinit var fakeDao: FakeDocumentDao
    private lateinit var repository: DocumentRepository

    @Before
    fun setUp() {
        fakeDao = FakeDocumentDao()
        repository = DocumentRepository(fakeDao)
    }

    @Test
    fun testClassifiedAadhaarDocumentReceivesAadhaarCard() = runBlocking {
        // 1. Classified Aadhaar document receives "Aadhaar Card"
        val ocrText = "Government of India Unique Identification Authority of India Aadhaar 1234 5678 9012"
        val originalFileName = "IMG_2026_09_Aadhaar.jpg"
        val classification = DocumentClassifier.classify(ocrText, originalFileName, "image/jpeg")

        val generatedName = repository.generateUniqueDisplayName(classification.documentType, classification.category)
        assertEquals("Aadhaar Card", generatedName)
    }

    @Test
    fun testClassifiedPanDocumentReceivesPanCard() = runBlocking {
        // 2. Classified PAN document receives "PAN Card"
        val ocrText = "INCOME TAX DEPARTMENT GOVT OF INDIA Permanent Account Number PAN ABCDE1234F"
        val originalFileName = "scan_pan_card.pdf"
        val classification = DocumentClassifier.classify(ocrText, originalFileName, "application/pdf")

        val generatedName = repository.generateUniqueDisplayName(classification.documentType, classification.category)
        assertEquals("PAN Card", generatedName)
    }

    @Test
    fun testMultipleSameTypeDocumentsReceiveUniqueNames() = runBlocking {
        // 3. Multiple same-type documents receive unique names: "Aadhaar Card", "Aadhaar Card (2)", "Aadhaar Card (3)"
        val name1 = repository.generateUniqueDisplayName("Aadhaar Card", "Identity")
        assertEquals("Aadhaar Card", name1)

        // Persist first document into Room
        fakeDao.insertDocument(
            DocumentEntity(
                id = 1L,
                originalName = "IMG_001.jpg",
                storedFileName = "IMG_001.jpg",
                displayName = name1,
                mimeType = "image/jpeg",
                originalUri = "content://media/1",
                internalPath = "/data/user/0/Identity/IMG_001.jpg",
                category = "Identity",
                documentType = "Aadhaar Card"
            )
        )

        // Second Aadhaar Card
        val name2 = repository.generateUniqueDisplayName("Aadhaar Card", "Identity")
        assertEquals("Aadhaar Card (2)", name2)

        // Persist second document
        fakeDao.insertDocument(
            DocumentEntity(
                id = 2L,
                originalName = "IMG_002.jpg",
                storedFileName = "IMG_002.jpg",
                displayName = name2,
                mimeType = "image/jpeg",
                originalUri = "content://media/2",
                internalPath = "/data/user/0/Identity/IMG_002.jpg",
                category = "Identity",
                documentType = "Aadhaar Card"
            )
        )

        // Third Aadhaar Card
        val name3 = repository.generateUniqueDisplayName("Aadhaar Card", "Identity")
        assertEquals("Aadhaar Card (3)", name3)

        // Likewise for Hall Ticket
        val htName1 = repository.generateUniqueDisplayName("Hall Ticket", "College")
        assertEquals("Hall Ticket", htName1)

        fakeDao.insertDocument(
            DocumentEntity(
                id = 3L,
                originalName = "Doc_101.pdf",
                storedFileName = "Doc_101.pdf",
                displayName = htName1,
                mimeType = "application/pdf",
                originalUri = "content://media/3",
                internalPath = "/data/user/0/College/Doc_101.pdf",
                category = "College",
                documentType = "Hall Ticket"
            )
        )

        val htName2 = repository.generateUniqueDisplayName("Hall Ticket", "College")
        assertEquals("Hall Ticket (2)", htName2)
    }

    @Test
    fun testOriginalFilenameRemainsUnchanged() = runBlocking {
        // 4. Original filename remains unchanged when display name is assigned
        val originalFileName = "IMG_8392.jpg"
        val autoDisplayName = repository.generateUniqueDisplayName("Aadhaar Card", "Identity")

        val doc = DocumentEntity(
            id = 1L,
            originalName = originalFileName,
            storedFileName = "Aadhaar_Card_1.jpg",
            displayName = autoDisplayName,
            mimeType = "image/jpeg",
            originalUri = "content://media/1",
            internalPath = "/data/user/0/Identity/Aadhaar_Card_1.jpg",
            category = "Identity",
            documentType = "Aadhaar Card"
        )
        fakeDao.insertDocument(doc)

        val retrieved = fakeDao.getDocumentById(1L)
        assertEquals("IMG_8392.jpg", retrieved?.originalName)
        assertEquals("Aadhaar Card", retrieved?.displayName)
    }

    @Test
    fun testUserRenamePersists() = runBlocking {
        // 5. User rename persists
        val doc = DocumentEntity(
            id = 1L,
            originalName = "IMG_8392.jpg",
            storedFileName = "IMG_8392.jpg",
            displayName = "Aadhaar Card",
            mimeType = "image/jpeg",
            originalUri = "content://media/1",
            internalPath = "/data/user/0/Identity/IMG_8392.jpg",
            category = "Identity",
            documentType = "Aadhaar Card"
        )
        fakeDao.insertDocument(doc)

        val renameResult = repository.updateDisplayName(1L, "Mom's Aadhaar Card")
        assertTrue(renameResult.isSuccess)

        val updated = fakeDao.getDocumentById(1L)
        assertEquals("Mom's Aadhaar Card", updated?.displayName)
        assertEquals("IMG_8392.jpg", updated?.originalName) // originalName intact
    }

    @Test
    fun testCancelRenamePreservesPreviousDisplayName() = runBlocking {
        // 6. Cancel rename preserves previous displayName
        val previousName = "Fee Receipt 2026"
        val doc = DocumentEntity(
            id = 1L,
            originalName = "Receipt_Spring.pdf",
            storedFileName = "Receipt_Spring.pdf",
            displayName = previousName,
            mimeType = "application/pdf",
            originalUri = "content://media/1",
            internalPath = "/data/user/0/Finance/Receipt_Spring.pdf",
            category = "Finance",
            documentType = "Payment Receipt"
        )
        fakeDao.insertDocument(doc)

        // Simulating Cancel action in UI (no repository update is executed)
        val loadedDoc = fakeDao.getDocumentById(1L)
        assertEquals(previousName, loadedDoc?.displayName)
    }

    @Test
    fun testEmptyOrInvalidRenameIsHandledCorrectly() = runBlocking {
        // 7. Empty/whitespace-only and duplicate rename is handled correctly
        val doc1 = DocumentEntity(
            id = 1L,
            originalName = "doc1.pdf",
            storedFileName = "doc1.pdf",
            displayName = "Salary Slip Jan",
            mimeType = "application/pdf",
            originalUri = "content://media/1",
            internalPath = "/data/user/0/Office/doc1.pdf",
            category = "Office",
            documentType = "Salary Slip"
        )
        val doc2 = DocumentEntity(
            id = 2L,
            originalName = "doc2.pdf",
            storedFileName = "doc2.pdf",
            displayName = "Salary Slip Feb",
            mimeType = "application/pdf",
            originalUri = "content://media/2",
            internalPath = "/data/user/0/Office/doc2.pdf",
            category = "Office",
            documentType = "Salary Slip"
        )
        fakeDao.insertDocument(doc1)
        fakeDao.insertDocument(doc2)

        // Empty string
        val emptyResult = repository.updateDisplayName(1L, "")
        assertTrue(emptyResult.isFailure)

        // Whitespace only
        val whitespaceResult = repository.updateDisplayName(1L, "   ")
        assertTrue(whitespaceResult.isFailure)

        // Name exceeding 100 characters
        val tooLongResult = repository.updateDisplayName(1L, "A".repeat(101))
        assertTrue(tooLongResult.isFailure)

        // Duplicate name (trying to rename doc1 to doc2's name)
        val duplicateResult = repository.updateDisplayName(1L, "Salary Slip Feb")
        assertTrue(duplicateResult.isFailure)

        // Name was not changed on failed validation
        assertEquals("Salary Slip Jan", fakeDao.getDocumentById(1L)?.displayName)
    }

    @Test
    fun testExistingDocumentsWithoutDisplayNameReceiveSafeDefault() {
        // 8. Existing documents without displayName receive a safe default via effectiveDisplayName
        val legacyDocWithType = DocumentEntity(
            id = 1L,
            originalName = "scan_001.pdf",
            storedFileName = "scan_001.pdf",
            displayName = "", // Empty displayName from older schema
            mimeType = "application/pdf",
            originalUri = "content://media/1",
            internalPath = "/data/user/0/College/scan_001.pdf",
            category = "College",
            documentType = "Hall Ticket"
        )
        assertEquals("Hall Ticket", legacyDocWithType.effectiveDisplayName)

        val legacyDocGeneric = DocumentEntity(
            id = 2L,
            originalName = "receipt_april.pdf",
            storedFileName = "receipt_april.pdf",
            displayName = "", // Empty displayName
            mimeType = "application/pdf",
            originalUri = "content://media/2",
            internalPath = "/data/user/0/Finance/receipt_april.pdf",
            category = "Finance",
            documentType = "Unclassified Document"
        )
        assertEquals("receipt_april.pdf", legacyDocGeneric.effectiveDisplayName)
    }

    @Test
    fun testRenamedDocumentDisplayNameSurvivesReloadFromRoom() = runBlocking {
        // 9. A renamed document's displayName survives reload from Room
        val doc = DocumentEntity(
            id = 55L,
            originalName = "IMG_9921.jpg",
            storedFileName = "IMG_9921.jpg",
            displayName = "Vehicle Registration Certificate",
            mimeType = "image/jpeg",
            originalUri = "content://media/55",
            internalPath = "/data/user/0/Vehicle/IMG_9921.jpg",
            category = "Vehicle",
            documentType = "Vehicle RC"
        )
        fakeDao.insertDocument(doc)

        // Rename
        repository.updateDisplayName(55L, "My Honda Civic RC")

        // Reload from DAO
        val reloaded = fakeDao.getDocumentById(55L)
        assertEquals("My Honda Civic RC", reloaded?.displayName)
        assertEquals("My Honda Civic RC", reloaded?.effectiveDisplayName)
    }
}
