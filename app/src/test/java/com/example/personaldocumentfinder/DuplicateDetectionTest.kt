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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeDocumentDao : DocumentDao {
    val documents = mutableListOf<DocumentEntity>()

    override suspend fun insertDocument(document: DocumentEntity): Long {
        documents.add(document)
        return document.id
    }

    override suspend fun updateDocument(document: DocumentEntity) {
        val index = documents.indexOfFirst { it.id == document.id }
        if (index != -1) {
            documents[index] = document
        }
    }
    override suspend fun deleteDocument(document: DocumentEntity) { documents.remove(document) }
    override suspend fun getDocumentById(id: Long): DocumentEntity? = documents.find { it.id == id }
    override suspend fun getDocumentByHash(hash: String): DocumentEntity? = documents.find { it.contentHash == hash }
    override suspend fun getDocumentByUri(uri: String): DocumentEntity? = documents.find { it.originalUri == uri }
    override suspend fun getDocumentByNameAndSize(name: String, size: Long): DocumentEntity? = documents.find { it.originalName == name && it.fileSize == size }
    override suspend fun getDocumentByDisplayName(displayName: String): DocumentEntity? = documents.find { it.displayName == displayName }
    override suspend fun getExistingDisplayNamesStartingWith(name: String): List<String> = documents.map { it.displayName }.filter { it == name || it.startsWith("$name (") }
    override fun getAllDocumentsFlow(): Flow<List<DocumentEntity>> = flowOf(documents)
    override fun getDocumentsByCategoryFlow(category: String): Flow<List<DocumentEntity>> = flowOf(documents.filter { it.category == category })
    override fun getFavoriteDocumentsFlow(): Flow<List<DocumentEntity>> = flowOf(documents.filter { it.isFavorite })

    override fun searchDocumentsFlow(query: String): Flow<List<DocumentEntity>> {
        val lower = query.lowercase().trim()
        if (lower.isEmpty()) return flowOf(documents)
        return flowOf(documents.filter { doc ->
            doc.displayName.lowercase().contains(lower) ||
            doc.originalName.lowercase().contains(lower) ||
            doc.ocrText.lowercase().contains(lower) ||
            doc.category.lowercase().contains(lower) ||
            doc.documentType.lowercase().contains(lower)
        })
    }

    override fun searchDocumentsMultiWordFlow(
        w1: String,
        w1_alt: String,
        w2: String,
        w2_alt: String,
        w3: String,
        w3_alt: String,
        w4: String,
        w4_alt: String,
        w5: String,
        w5_alt: String
    ): Flow<List<DocumentEntity>> {
        val pairs = listOf(w1 to w1_alt, w2 to w2_alt, w3 to w3_alt, w4 to w4_alt, w5 to w5_alt)
            .filter { it.first.trim().isNotEmpty() }
            .map { (w, alt) -> listOf(w.lowercase().trim(), alt.lowercase().trim()).filter { it.isNotEmpty() } }

        if (pairs.isEmpty()) return flowOf(documents)
        return flowOf(documents.filter { doc ->
            val docFields = listOf(
                doc.displayName.lowercase(),
                doc.originalName.lowercase(),
                doc.ocrText.lowercase(),
                doc.category.lowercase(),
                doc.documentType.lowercase()
            )
            pairs.all { termVariants ->
                termVariants.any { variant ->
                    docFields.any { it.contains(variant) }
                }
            }
        })
    }

    override fun getCountByCategoryFlow(category: String): Flow<Int> = flowOf(documents.count { it.category == category })
    override fun getTotalDocumentCountFlow(): Flow<Int> = flowOf(documents.size)
    override suspend fun updateCategory(id: Long, newCategory: String, timestamp: Long) {}
    override suspend fun updateDisplayName(id: Long, newDisplayName: String, timestamp: Long) {
        val index = documents.indexOfFirst { it.id == id }
        if (index != -1) {
            documents[index] = documents[index].copy(displayName = newDisplayName, lastModified = timestamp)
        }
    }
    override suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {}
}

class TestStorageManager : StorageManager(StubContext()) {
    var stubHash: String? = "sha256_hash_abc123"

    override fun calculateContentHash(uri: Uri): String? = stubHash
}

class StubContext : android.content.ContextWrapper(null)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
        testStorageManager.stubHash = "different_hash_xyz"

        val filtered = candidateRepository.filterUnimportedCandidates(testStorageManager, listOf(candidate))
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun testDuplicateContentHashCandidateIsSkipped() = runBlocking {
        val uri = Uri.parse("content://media/external/file/102")
        val candidate = DiscoveredCandidate(
            uri = uri,
            name = "Copy_Of_HallTicket.pdf",
            mimeType = "application/pdf",
            fileSize = 102400L,
            filePath = "/storage/emulated/0/Download/Copy_Of_HallTicket.pdf",
            dateModified = System.currentTimeMillis()
        )

        fakeDao.insertDocument(
            DocumentEntity(
                id = 1L,
                originalName = "Original_HallTicket.pdf",
                storedFileName = "Original_HallTicket.pdf",
                mimeType = "application/pdf",
                originalUri = "content://media/external/file/999",
                internalPath = "/data/user/0/com.example/files/College/Original_HallTicket.pdf",
                category = "College",
                documentType = "Hall Ticket",
                contentHash = "same_content_hash_123"
            )
        )

        val testStorageManager = TestStorageManager()
        testStorageManager.stubHash = "same_content_hash_123"

        val filtered = candidateRepository.filterUnimportedCandidates(testStorageManager, listOf(candidate))
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun testSameNameAndSizeCandidateIsSkipped() = runBlocking {
        val uri = Uri.parse("content://media/external/file/103")
        val candidate = DiscoveredCandidate(
            uri = uri,
            name = "ImportantDoc.pdf",
            mimeType = "application/pdf",
            fileSize = 51200L,
            filePath = "/storage/emulated/0/Download/ImportantDoc.pdf",
            dateModified = System.currentTimeMillis()
        )

        fakeDao.insertDocument(
            DocumentEntity(
                id = 2L,
                originalName = "ImportantDoc.pdf",
                storedFileName = "ImportantDoc.pdf",
                mimeType = "application/pdf",
                originalUri = "content://media/external/file/888",
                internalPath = "/data/user/0/com.example/files/Other/ImportantDoc.pdf",
                category = "Other Documents",
                documentType = "Other Document",
                fileSize = 51200L,
                contentHash = ""
            )
        )

        val testStorageManager = TestStorageManager()
        testStorageManager.stubHash = "random_hash"

        val filtered = candidateRepository.filterUnimportedCandidates(testStorageManager, listOf(candidate))
        assertTrue(filtered.isEmpty())
    }
}
