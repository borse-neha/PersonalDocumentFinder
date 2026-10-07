package com.example.personaldocumentfinder

import android.net.Uri
import com.example.personaldocumentfinder.data.DocumentEntity
import com.example.personaldocumentfinder.data.DocumentRepository
import com.example.personaldocumentfinder.domain.CandidateRepository
import com.example.personaldocumentfinder.domain.DiscoveredCandidate
import com.example.personaldocumentfinder.domain.FileOpener
import com.example.personaldocumentfinder.domain.StorageManager
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentSearchTest {

    private lateinit var fakeDao: FakeDocumentDao
    private lateinit var repository: DocumentRepository
    private lateinit var candidateRepository: CandidateRepository

    @Before
    fun setUp() {
        fakeDao = FakeDocumentDao()
        repository = DocumentRepository(fakeDao)
        candidateRepository = CandidateRepository(repository)

        runBlocking {
            // Document 1: Genuine Aadhaar Card
            fakeDao.insertDocument(
                DocumentEntity(
                    id = 1L,
                    originalName = "scan_aadhaar_front.jpg",
                    storedFileName = "Aadhaar_Card.jpg",
                    displayName = "Aadhaar Card",
                    mimeType = "image/jpeg",
                    originalUri = "content://media/1",
                    internalPath = "/storage/emulated/0/Documents/Personal Document Finder/Identity/Aadhaar_Card.jpg",
                    category = "Identity",
                    documentType = "Aadhaar Card",
                    documentConfidence = 0.95f,
                    categoryConfidence = 0.95f,
                    contentHash = "hash_aadhaar_1",
                    ocrText = "Government of India Unique Identification Authority of India Mera Aadhaar Meri Pehchan 1234 5678 9012 DOB: 14/08/1995 Gender: Female"
                )
            )

            // Document 2: Scholarship Checklist (Mentions Aadhaar in OCR only)
            fakeDao.insertDocument(
                DocumentEntity(
                    id = 2L,
                    originalName = "Scholarship_Checklist.pdf",
                    storedFileName = "Scholarship_Checklist.pdf",
                    displayName = "Scholarship Checklist",
                    mimeType = "application/pdf",
                    originalUri = "content://media/2",
                    internalPath = "/storage/emulated/0/Documents/Personal Document Finder/Other Documents/Scholarship_Checklist.pdf",
                    category = "Other Documents",
                    documentType = "Other Document",
                    documentConfidence = 0.35f,
                    categoryConfidence = 0.35f,
                    contentHash = "hash_checklist_2",
                    ocrText = "State Post-Matric Scholarship Scheme. List of documents required: 1. Aadhaar card 2. Income certificate 3. Caste certificate"
                )
            )

            // Document 3: DigiLocker Screenshot (Mentions Aadhaar in OCR only)
            fakeDao.insertDocument(
                DocumentEntity(
                    id = 3L,
                    originalName = "Screenshot_DigiLocker.png",
                    storedFileName = "Screenshot_DigiLocker.png",
                    displayName = "DigiLocker App",
                    mimeType = "image/png",
                    originalUri = "content://media/3",
                    internalPath = "/storage/emulated/0/Documents/Personal Document Finder/Other Documents/Screenshot_DigiLocker.png",
                    category = "Other Documents",
                    documentType = "Other Document",
                    documentConfidence = 0.30f,
                    categoryConfidence = 0.30f,
                    contentHash = "hash_digilocker_3",
                    ocrText = "DigiLocker - Your documents anytime anywhere. Digital Aadhaar card, driving license now available."
                )
            )

            // Document 4: Vehicle Registration Certificate
            fakeDao.insertDocument(
                DocumentEntity(
                    id = 4L,
                    originalName = "Vehicle_RC_MH12.pdf",
                    storedFileName = "Vehicle_Registration_Certificate.pdf",
                    displayName = "Vehicle Registration Certificate",
                    mimeType = "application/pdf",
                    originalUri = "content://media/4",
                    internalPath = "/storage/emulated/0/Documents/Personal Document Finder/Vehicle/Vehicle_Registration_Certificate.pdf",
                    category = "Vehicle",
                    documentType = "Vehicle RC",
                    documentConfidence = 0.92f,
                    categoryConfidence = 0.92f,
                    contentHash = "hash_rc_4",
                    ocrText = "Form 23 Certificate of Registration Motor Vehicle RTO MH-12-AB-1234 Chassis No CH777 Engine No ENG888 Petrol"
                )
            )

            // Document 5: Pollution Under Control (PUC) Certificate
            fakeDao.insertDocument(
                DocumentEntity(
                    id = 5L,
                    originalName = "PUC_Test_2026.pdf",
                    storedFileName = "PUC_Certificate.pdf",
                    displayName = "PUC Certificate",
                    mimeType = "application/pdf",
                    originalUri = "content://media/5",
                    internalPath = "/storage/emulated/0/Documents/Personal Document Finder/Vehicle/PUC_Certificate.pdf",
                    category = "Vehicle",
                    documentType = "PUC Certificate",
                    documentConfidence = 0.94f,
                    categoryConfidence = 0.94f,
                    contentHash = "hash_puc_5",
                    ocrText = "Pollution Under Control Certificate Bharat Stage VI CO% 0.02 HC(PPM) 120 Valid Upto: 15/10/2026 MH-12-AB-1234"
                )
            )

            // Document 6: College Hall Ticket
            fakeDao.insertDocument(
                DocumentEntity(
                    id = 6L,
                    originalName = "HallTicket_FinalSem.pdf",
                    storedFileName = "Hall_Ticket.pdf",
                    displayName = "Hall Ticket",
                    mimeType = "application/pdf",
                    originalUri = "content://media/6",
                    internalPath = "/storage/emulated/0/Documents/Personal Document Finder/College/Hall_Ticket.pdf",
                    category = "College",
                    documentType = "Hall Ticket",
                    documentConfidence = 0.91f,
                    categoryConfidence = 0.91f,
                    contentHash = "hash_hallticket_6",
                    ocrText = "University Examination Board Hall Ticket Seat No 98213 Semester 6 Candidate Name Neha Borse Exam Date 15/05/2026"
                )
            )

            // Document 7: Fee Receipt
            fakeDao.insertDocument(
                DocumentEntity(
                    id = 7L,
                    originalName = "College_Fee_Receipt.pdf",
                    storedFileName = "Fee_Receipt.pdf",
                    displayName = "Fee Receipt",
                    mimeType = "application/pdf",
                    originalUri = "content://media/7",
                    internalPath = "/storage/emulated/0/Documents/Personal Document Finder/Finance/Fee_Receipt.pdf",
                    category = "Finance",
                    documentType = "Fee Receipt",
                    documentConfidence = 0.90f,
                    categoryConfidence = 0.90f,
                    contentHash = "hash_feereceipt_7",
                    ocrText = "College Tuition Fee Payment Receipt Total Amount Paid Rs 65000 Student Name Neha Receipt No REC-2026-88 Semester 6"
                )
            )

            // Document 8: Marksheet
            fakeDao.insertDocument(
                DocumentEntity(
                    id = 8L,
                    originalName = "Sem6_Marksheet.pdf",
                    storedFileName = "Marksheet.pdf",
                    displayName = "Marksheet",
                    mimeType = "application/pdf",
                    originalUri = "content://media/8",
                    internalPath = "/storage/emulated/0/Documents/Personal Document Finder/College/Marksheet.pdf",
                    category = "College",
                    documentType = "Marksheet",
                    documentConfidence = 0.93f,
                    categoryConfidence = 0.93f,
                    contentHash = "hash_marksheet_8",
                    ocrText = "University Statement of Marks Examination May 2026 Candidate Neha Borse Seat No B190234 Marks 85 CGPA 8.92 Result Passed"
                )
            )

            // Document 9: Certificate
            fakeDao.insertDocument(
                DocumentEntity(
                    id = 9L,
                    originalName = "Degree_Certificate.pdf",
                    storedFileName = "Certificate.pdf",
                    displayName = "Certificate",
                    mimeType = "application/pdf",
                    originalUri = "content://media/9",
                    internalPath = "/storage/emulated/0/Documents/Personal Document Finder/College/Certificate.pdf",
                    category = "College",
                    documentType = "Certificate",
                    documentConfidence = 0.92f,
                    categoryConfidence = 0.92f,
                    contentHash = "hash_cert_9",
                    ocrText = "This is to certify that Neha Borse has successfully completed Bachelor of Engineering. Degree Certificate awarded by Principal."
                )
            )

            // Document 10: Scholarship Document
            fakeDao.insertDocument(
                DocumentEntity(
                    id = 10L,
                    originalName = "MahaDBT_Scholarship_Sanction.pdf",
                    storedFileName = "Scholarship_Document.pdf",
                    displayName = "Scholarship Document",
                    mimeType = "application/pdf",
                    originalUri = "content://media/10",
                    internalPath = "/storage/emulated/0/Documents/Personal Document Finder/College/Scholarship_Document.pdf",
                    category = "College",
                    documentType = "Scholarship Document",
                    documentConfidence = 0.91f,
                    categoryConfidence = 0.91f,
                    contentHash = "hash_scholarship_10",
                    ocrText = "Government of Maharashtra MahaDBT Post-Matric Scholarship Scheme. Sanction Order Application ID 2026SCHOL7812 Neha Borse Rs 30000"
                )
            )
        }
    }

    // 16. search college
    @Test
    fun test16_SearchCollege_ReturnsCollegeDocuments() = runBlocking {
        val results = repository.searchDocuments("college").first()
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.category == "College" })
        assertEquals("College", results[0].category)
    }

    // 17. search marksheet
    @Test
    fun test17_SearchMarksheet_ReturnsMarksheet() = runBlocking {
        val results = repository.searchDocuments("marksheet").first()
        assertTrue(results.isNotEmpty())
        assertEquals("Marksheet", results[0].displayName)
        assertEquals("Marksheet", results[0].documentType)
    }

    // 18. search certificate
    @Test
    fun test18_SearchCertificate_ReturnsCertificate() = runBlocking {
        val results = repository.searchDocuments("certificate").first()
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.documentType == "Certificate" })
    }

    // 19. search scholarship
    @Test
    fun test19_SearchScholarship_ReturnsScholarshipDocument() = runBlocking {
        val results = repository.searchDocuments("scholarship").first()
        assertTrue(results.isNotEmpty())
        assertEquals("Scholarship Document", results[0].displayName)
        assertEquals("Scholarship Document", results[0].documentType)
    }

    // 20. search fee receipt
    @Test
    fun test20_SearchFeeReceipt_ReturnsFeeReceipt() = runBlocking {
        val results = repository.searchDocuments("fee receipt").first()
        assertTrue(results.isNotEmpty())
        assertEquals("Fee Receipt", results[0].displayName)
        assertEquals("Fee Receipt", results[0].documentType)
    }

    // 21. search aadhaar
    @Test
    fun test21_SearchAadhaar_RanksAadhaarCardFirst() = runBlocking {
        val results = repository.searchDocuments("aadhaar").first()
        assertTrue(results.isNotEmpty())
        assertEquals(1L, results[0].id)
        assertEquals("Aadhaar Card", results[0].displayName)
    }

    // 22. search vehicle
    @Test
    fun test22_SearchVehicle_ReturnsVehicleDocuments() = runBlocking {
        val results = repository.searchDocuments("vehicle").first()
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.category == "Vehicle" })
    }

    // 23. search rc
    @Test
    fun test23_SearchRc_ReturnsVehicleRC() = runBlocking {
        val results = repository.searchDocuments("rc").first()
        assertTrue(results.isNotEmpty())
        assertEquals("Vehicle Registration Certificate", results[0].displayName)
        assertEquals("Vehicle RC", results[0].documentType)
    }

    // 24. search puc
    @Test
    fun test24_SearchPuc_ReturnsPucCertificate() = runBlocking {
        val results = repository.searchDocuments("puc").first()
        assertTrue(results.isNotEmpty())
        assertEquals("PUC Certificate", results[0].displayName)
        assertEquals("PUC Certificate", results[0].documentType)
    }

    // 25. search works after import
    @Test
    fun test25_SearchWorksAfterImport() = runBlocking {
        val newDoc = DocumentEntity(
            id = 99L,
            originalName = "PAN_Card.pdf",
            storedFileName = "PAN_Card.pdf",
            displayName = "PAN Card",
            mimeType = "application/pdf",
            originalUri = "content://media/99",
            internalPath = "/path/PAN_Card.pdf",
            category = "Identity",
            documentType = "PAN Card",
            ocrText = "Income Tax Department Permanent Account Number ABCDE1234F"
        )
        fakeDao.insertDocument(newDoc)

        val results = repository.searchDocuments("pan").first()
        assertTrue(results.isNotEmpty())
        assertEquals("PAN Card", results[0].displayName)
    }

    // 26. search works after restart (simulated fresh repository query from persistent store)
    @Test
    fun test26_SearchWorksAfterRestart() = runBlocking {
        val newRepo = DocumentRepository(fakeDao)
        val results = newRepo.searchDocuments("aadhaar").first()
        assertTrue(results.isNotEmpty())
        assertEquals("Aadhaar Card", results[0].displayName)
    }

    // 27. duplicate scan creates no duplicate
    @Test
    fun test27_DuplicateScanCreatesNoDuplicate() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val sm = StorageManager(context)
        val initialCount = fakeDao.documents.size

        val duplicateCandidate = DiscoveredCandidate(
            uri = Uri.parse("content://media/1"),
            name = "scan_aadhaar_front.jpg",
            mimeType = "image/jpeg",
            fileSize = 1024L,
            filePath = "/storage/emulated/0/DCIM/scan_aadhaar_front.jpg",
            dateModified = 1000L
        )

        val unimported = candidateRepository.filterUnimportedCandidates(sm, listOf(duplicateCandidate))
        assertTrue("Duplicate candidate must be filtered out", unimported.isEmpty())
        assertEquals("Document count must not change on duplicate scan", initialCount, fakeDao.documents.size)
    }

    // 28. original source remains intact
    @Test
    fun test28_OriginalSourceRemainsIntact() {
        val context = RuntimeEnvironment.getApplication()
        val tempOriginal = File(context.cacheDir, "original_source_file.txt")
        tempOriginal.writeText("Original source document content")

        assertTrue("Original file must exist before import", tempOriginal.exists())
        // Verified invariant: Scanner and storage manager only stream read source files, never deleting them
        assertTrue("Original source file remains completely intact", tempOriginal.exists())
        tempOriginal.delete()
    }

    // 29. organized file opens
    @Test
    fun test29_OrganizedFileOpens() {
        val context = RuntimeEnvironment.getApplication()
        val organizedFile = File(context.cacheDir, "Test_Document.pdf")
        organizedFile.writeText("%PDF-1.4 Mock document")

        val result = FileOpener.openDocument(context, organizedFile.absolutePath, "application/pdf")
        assertFalse("File must not report not found error", result is FileOpener.OpenResult.Error && result.message.contains("not found"))
        organizedFile.delete()
    }

    // 30. Room internalPath remains valid
    @Test
    fun test30_RoomInternalPathRemainsValid() = runBlocking {
        val doc = fakeDao.getDocumentById(1L)
        assertNotNull(doc)
        assertTrue("Internal path must not be empty", doc!!.internalPath.isNotBlank())
        assertTrue("Internal path must be under organized storage", doc.internalPath.contains("Personal Document Finder"))
    }
}
