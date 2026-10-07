package com.example.personaldocumentfinder

import android.content.Context
import android.net.Uri
import com.example.personaldocumentfinder.data.DocumentEntity
import com.example.personaldocumentfinder.data.DocumentRepository
import com.example.personaldocumentfinder.domain.CandidateRepository
import com.example.personaldocumentfinder.domain.DiscoveredCandidate
import com.example.personaldocumentfinder.domain.FileOpener
import com.example.personaldocumentfinder.domain.StorageManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentStorageTest {

    private lateinit var context: Context
    private lateinit var fakeDao: FakeDocumentDao
    private lateinit var repository: DocumentRepository
    private lateinit var candidateRepository: CandidateRepository
    private lateinit var storageManager: StorageManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fakeDao = FakeDocumentDao()
        repository = DocumentRepository(fakeDao)
        candidateRepository = CandidateRepository(repository)
        storageManager = StorageManager(context)
    }

    private fun createDummySourceFile(fileName: String, content: String): File {
        val originalDir = File(context.cacheDir, "original_user_documents")
        if (!originalDir.exists()) originalDir.mkdirs()
        val originalFile = File(originalDir, fileName)
        FileOutputStream(originalFile).use { it.write(content.toByteArray()) }
        return originalFile
    }

    // 30. Classified document is physically copied to correct phone storage category.
    @Test
    fun test30_ClassifiedDocumentPhysicallyCopiedToCorrectPhoneStorageCategory() {
        val original = createDummySourceFile("Aadhaar_Original.jpg", "Aadhaar Card Data 1234 5678 9012")
        val uri = Uri.fromFile(original)

        val copyResult = storageManager.copyFileToOrganizedStorage(uri, "Identity", "Aadhaar_Card.jpg")
        assertNotNull(copyResult)

        val organizedFile = File(copyResult!!.internalPath)
        assertTrue("Organized file must physically exist", organizedFile.exists())
        assertEquals("Aadhaar_Card.jpg", organizedFile.name)
        assertEquals("Identity", organizedFile.parentFile?.name)
    }

    // 31. Database points to the organized file.
    @Test
    fun test31_DatabasePointsToTheOrganizedFile() = runBlocking {
        val original = createDummySourceFile("Vehicle_RC_Original.pdf", "Motor Vehicle Registration MH-12-AB-1234")
        val uri = Uri.fromFile(original)

        val copyResult = storageManager.copyFileToOrganizedStorage(uri, "Vehicle", "Vehicle_RC.pdf")
        assertNotNull(copyResult)

        val entity = DocumentEntity(
            id = 31L,
            originalName = "Vehicle_RC_Original.pdf",
            storedFileName = copyResult!!.storedFileName,
            displayName = "Vehicle Registration Certificate",
            mimeType = "application/pdf",
            originalUri = uri.toString(),
            internalPath = copyResult.internalPath,
            category = "Vehicle",
            documentType = "Vehicle RC",
            contentHash = copyResult.contentHash
        )
        fakeDao.insertDocument(entity)

        val retrieved = repository.getDocumentById(31L)
        assertNotNull(retrieved)
        assertEquals(copyResult.internalPath, retrieved!!.internalPath)
        val fileOnDisk = File(retrieved.internalPath)
        assertTrue("File pointed to by DB must exist on disk", fileOnDisk.exists())
    }

    // 32. File can be opened after simulated app restart.
    @Test
    fun test32_FileCanBeOpenedAfterAppRestart() = runBlocking {
        val original = createDummySourceFile("HallTicket.pdf", "University Examination Hall Ticket")
        val uri = Uri.fromFile(original)
        val copyResult = storageManager.copyFileToOrganizedStorage(uri, "College", "Hall_Ticket.pdf")!!

        val entity = DocumentEntity(
            id = 32L,
            originalName = "HallTicket.pdf",
            storedFileName = copyResult.storedFileName,
            displayName = "Hall Ticket",
            mimeType = "application/pdf",
            originalUri = uri.toString(),
            internalPath = copyResult.internalPath,
            category = "College",
            documentType = "Hall Ticket"
        )
        fakeDao.insertDocument(entity)

        // Simulate app restart: re-initialize repository & storage manager
        val newRepository = DocumentRepository(fakeDao)
        val restoredDoc = newRepository.getDocumentById(32L)
        assertNotNull(restoredDoc)

        val targetFile = File(restoredDoc!!.internalPath)
        assertTrue("File must still exist after restart", targetFile.exists())
        assertTrue("File must be readable", targetFile.canRead())
        assertEquals("File size must match original content", original.length(), targetFile.length())

        // FileOpener checks file existence first; ensure it does not fail with "file no longer exists"
        val nonExistentResult = FileOpener.openDocument(context, "/invalid/path/missing.pdf", "application/pdf")
        assertTrue(nonExistentResult is FileOpener.OpenResult.Error)
        assertEquals("Document file no longer exists in app storage.", (nonExistentResult as FileOpener.OpenResult.Error).message)

        val existingResult = FileOpener.openDocument(context, restoredDoc.internalPath, restoredDoc.mimeType)
        // Ensure existingResult does NOT report file missing
        if (existingResult is FileOpener.OpenResult.Error) {
            assertFalse(
                "Must not report file does not exist",
                existingResult.message.contains("no longer exists")
            )
        }
    }

    // 33. Original file is preserved.
    @Test
    fun test33_OriginalFileIsPreserved() {
        val original = createDummySourceFile("Important_Doc.pdf", "Original Unmodified Content")
        val originalLength = original.length()
        val uri = Uri.fromFile(original)

        val copyResult = storageManager.copyFileToOrganizedStorage(uri, "Personal", "Important_Doc.pdf")
        assertNotNull(copyResult)

        assertTrue("Original file must remain intact", original.exists())
        assertEquals("Original file size must remain unmodified", originalLength, original.length())
    }

    // 34. Duplicate scan does not create another copy.
    @Test
    fun test34_DuplicateScanDoesNotCreateAnotherCopy() = runBlocking {
        val original = createDummySourceFile("Scan_Duplicate.pdf", "Duplicate Content Test")
        val uri = Uri.fromFile(original)
        val copyResult = storageManager.copyFileToOrganizedStorage(uri, "Other Documents", "Scan_Duplicate.pdf")!!

        val entity = DocumentEntity(
            id = 34L,
            originalName = "Scan_Duplicate.pdf",
            storedFileName = copyResult.storedFileName,
            displayName = "Other Document",
            mimeType = "application/pdf",
            originalUri = uri.toString(),
            internalPath = copyResult.internalPath,
            category = "Other Documents",
            documentType = "Other Document",
            contentHash = copyResult.contentHash
        )
        fakeDao.insertDocument(entity)

        val candidate = DiscoveredCandidate(
            uri = uri,
            name = "Scan_Duplicate.pdf",
            mimeType = "application/pdf",
            fileSize = original.length(),
            filePath = original.absolutePath,
            dateModified = System.currentTimeMillis()
        )

        val unimported = candidateRepository.filterUnimportedCandidates(storageManager, listOf(candidate))
        assertTrue("Duplicate candidate must be filtered out to prevent duplicate copies", unimported.isEmpty())
    }

    // 35. Zero-byte files are not copied or stored.
    @Test
    fun test35_ZeroByteFileIsNotCopiedOrStored() {
        val emptyOriginal = createDummySourceFile("Empty_Doc.pdf", "")
        val uri = Uri.fromFile(emptyOriginal)

        val copyResult = storageManager.copyFileToOrganizedStorage(uri, "Other Documents", "Empty_Doc.pdf")
        assertTrue("Zero-byte file must not be copied", copyResult == null)

        val categoryDir = storageManager.getCategoryDir("Other Documents")
        val possibleFile = File(categoryDir, "Empty_Doc.pdf")
        assertFalse("Empty file must not exist in storage", possibleFile.exists())
    }

    // 36. deleteAppPrivateFile rejects deleting external user files outside app boundary.
    @Test
    fun test36_DeleteAppPrivateFileRejectsDeletingExternalUserFiles() {
        val externalUserFile = createDummySourceFile("External_Photo.jpg", "Precious User Photo Content")
        assertTrue("Original external user file must exist", externalUserFile.exists())

        // Attempting to delete external user file path directly must fail and not delete the file
        val deletionResult = storageManager.deleteAppPrivateFile(externalUserFile.absolutePath)
        assertFalse("Must refuse to delete files outside app boundary", deletionResult)
        assertTrue("External user file must remain intact", externalUserFile.exists())
    }

    // 37. Moving file to same category returns existing path without creating copies.
    @Test
    fun test37_MoveFileToSameCategoryReturnsExistingPathWithoutDuplication() {
        val original = createDummySourceFile("Doc_To_Move.pdf", "Content to Move")
        val uri = Uri.fromFile(original)
        val copyResult = storageManager.copyFileToOrganizedStorage(uri, "Finance", "Doc_To_Move.pdf")!!

        val initialPath = copyResult.internalPath
        val sameCategoryPath = storageManager.moveFileToCategory(initialPath, "Finance")
        assertNotNull(sameCategoryPath)
        assertEquals("Path must remain identical when moving to the same category", initialPath, sameCategoryPath)
    }
}
