package com.example.personaldocumentfinder

import android.net.Uri
import com.example.personaldocumentfinder.data.DocumentDao
import com.example.personaldocumentfinder.data.DocumentEntity
import com.example.personaldocumentfinder.data.DocumentRepository
import com.example.personaldocumentfinder.domain.CandidateRepository
import com.example.personaldocumentfinder.domain.DiscoveredCandidate
import com.example.personaldocumentfinder.domain.StorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeDocumentDao : DocumentDao {
    val documents = mutableListOf<DocumentEntity>()

    override suspend fun insertDocument(document: DocumentEntity): Long {
        documents.add(document)
        return document.id
    }

    override suspend fun updateDocument(document: DocumentEntity) {}
    override suspend fun deleteDocument(document: DocumentEntity) { documents.remove(document) }
    override suspend fun getDocumentById(id: Long): DocumentEntity? = documents.find { it.id == id }
    override suspend fun getDocumentByHash(hash: String): DocumentEntity? = documents.find { it.contentHash == hash }
    override suspend fun getDocumentByUri(uri: String): DocumentEntity? = documents.find { it.originalUri == uri }
    override suspend fun getDocumentByNameAndSize(name: String, size: Long): DocumentEntity? = documents.find { it.originalName == name && it.fileSize == size }
    override fun getAllDocumentsFlow(): Flow<List<DocumentEntity>> = flowOf(documents)
    override fun getDocumentsByCategoryFlow(category: String): Flow<List<DocumentEntity>> = flowOf(documents.filter { it.category == category })
    override fun getFavoriteDocumentsFlow(): Flow<List<DocumentEntity>> = flowOf(documents.filter { it.isFavorite })
    override fun searchDocumentsFlow(query: String): Flow<List<DocumentEntity>> = flowOf(documents)
    override fun getCountByCategoryFlow(category: String): Flow<Int> = flowOf(documents.count { it.category == category })
    override fun getTotalDocumentCountFlow(): Flow<Int> = flowOf(documents.size)
    override suspend fun updateCategory(id: Long, newCategory: String, timestamp: Long) {}
    override suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {}
}

class TestStorageManager : StorageManager(StubContext()) {
    var stubHash: String? = "sha256_hash_abc123"

    override fun calculateContentHash(uri: Uri): String? = stubHash
}

class StubContext : android.content.ContextWrapper(null)

class DuplicateDetectionTest {

    private lateinit var fakeDao: FakeDocumentDao
    private lateinit var repository: DocumentRepository
    private lateinit var candidateRepository: CandidateRepository

    @Before
    fun setUp() {
        fakeDao = FakeDocumentDao()
        repository = DocumentRepository(fakeDao)
        candidateRepository = CandidateRepository(repository)
    }

    @Test
    fun testUnimportedCandidatePassesFilter() = runBlocking {
        val uri = Uri.parse("content://media/external/file/101")
        val candidate = DiscoveredCandidate(
            uri = uri,
            name = "College_HallTicket.pdf",
            mimeType = "application/pdf",
            fileSize = 102400L,
            filePath = "/storage/emulated/0/Download/College_HallTicket.pdf",
            dateModified = System.currentTimeMillis()
        )

        val testStorageManager = TestStorageManager()
        testStorageManager.stubHash = "sha256_hash_abc123"

        val filtered = candidateRepository.filterUnimportedCandidates(testStorageManager, listOf(candidate))

        assertEquals(1, filtered.size)
        assertEquals("College_HallTicket.pdf", filtered[0].name)
    }

    @Test
    fun testDuplicateUriCandidateIsSkipped() = runBlocking {
        val uri = Uri.parse("content://media/external/file/101")
        val candidate = DiscoveredCandidate(
            uri = uri,
            name = "College_HallTicket.pdf",
            mimeType = "application/pdf",
            fileSize = 102400L,
            filePath = "/storage/emulated/0/Download/College_HallTicket.pdf",
            dateModified = System.currentTimeMillis()
        )

        fakeDao.insertDocument(
            DocumentEntity(
                id = 1L,
                originalName = "College_HallTicket.pdf",
                storedFileName = "College_HallTicket.pdf",
                mimeType = "application/pdf",
                originalUri = "content://media/external/file/101",
                internalPath = "/data/user/0/com.example/files/College/College_HallTicket.pdf",
                category = "College",
                documentType = "Hall Ticket"
            )
        )

        val testStorageManager = TestStorageManager()

        val filtered = candidateRepository.filterUnimportedCandidates(testStorageManager, listOf(candidate))

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun testRenamedIdenticalContentIsSkippedByHash() = runBlocking {
        val uri = Uri.parse("content://media/external/file/202")
        val candidate = DiscoveredCandidate(
            uri = uri,
            name = "random_8392.jpg", // Renamed filename
            mimeType = "image/jpeg",
            fileSize = 204800L,
            filePath = "/storage/emulated/0/Pictures/random_8392.jpg",
            dateModified = System.currentTimeMillis()
        )

        fakeDao.insertDocument(
            DocumentEntity(
                id = 2L,
                originalName = "Aadhaar_1.jpg", // Original imported filename
                storedFileName = "Aadhaar_1.jpg",
                mimeType = "image/jpeg",
                originalUri = "content://media/external/file/101",
                internalPath = "/data/user/0/com.example/files/Identity/Aadhaar_1.jpg",
                category = "Identity",
                documentType = "Aadhaar Card",
                contentHash = "identical_sha256_hash_xyz999"
            )
        )

        val testStorageManager = TestStorageManager()
        testStorageManager.stubHash = "identical_sha256_hash_xyz999"

        val filtered = candidateRepository.filterUnimportedCandidates(testStorageManager, listOf(candidate))

        assertTrue(filtered.isEmpty()) // Renamed file with identical SHA-256 content is skipped!
    }
}
