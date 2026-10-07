package com.example.personaldocumentfinder.data

import com.example.personaldocumentfinder.domain.DocumentSearchEngine
import com.example.personaldocumentfinder.domain.RankedSearchResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DocumentRepository(private val documentDao: DocumentDao) {

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocumentsFlow()
    val favoriteDocuments: Flow<List<DocumentEntity>> = documentDao.getFavoriteDocumentsFlow()
    val totalCount: Flow<Int> = documentDao.getTotalDocumentCountFlow()

    fun getDocumentsByCategory(category: String): Flow<List<DocumentEntity>> {
        return documentDao.getDocumentsByCategoryFlow(category)
    }

    fun getCategoryCount(category: String): Flow<Int> {
        return documentDao.getCountByCategoryFlow(category)
    }

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return allDocuments
        }
        return allDocuments.map { candidates ->
            DocumentSearchEngine.rankAndGroupDocuments(candidates, trimmed).allRanked
        }
    }

    fun searchRankedDocuments(query: String): Flow<RankedSearchResults> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return allDocuments.map { RankedSearchResults(primaryMatches = it, mentionMatches = emptyList()) }
        }
        return allDocuments.map { candidates ->
            DocumentSearchEngine.rankAndGroupDocuments(candidates, trimmed)
        }
    }

    suspend fun getDocumentById(id: Long): DocumentEntity? {
        return documentDao.getDocumentById(id)
    }

    suspend fun getDocumentByUri(uri: String): DocumentEntity? {
        return documentDao.getDocumentByUri(uri)
    }

    suspend fun getDocumentByNameAndSize(name: String, size: Long): DocumentEntity? {
        return documentDao.getDocumentByNameAndSize(name, size)
    }

    suspend fun getDocumentByHash(contentHash: String): DocumentEntity? {
        return documentDao.getDocumentByHash(contentHash)
    }

    suspend fun insertDocument(document: DocumentEntity): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: DocumentEntity) {
        documentDao.updateDocument(document)
    }

    suspend fun updateCategory(id: Long, newCategory: String) {
        documentDao.updateCategory(id, newCategory)
    }

    suspend fun renameDocument(id: Long, newName: String): Result<Unit> {
        return updateDisplayName(id, newName)
    }

    suspend fun updateDisplayName(id: Long, newName: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Document name cannot be empty."))
        }
        if (trimmed.length > 100) {
            return Result.failure(IllegalArgumentException("Document name cannot exceed 100 characters."))
        }
        val existingDoc = documentDao.getDocumentByDisplayName(trimmed)
        if (existingDoc != null && existingDoc.id != id) {
            return Result.failure(IllegalArgumentException("A document named '$trimmed' already exists."))
        }
        documentDao.updateDisplayName(id, trimmed)
        return Result.success(Unit)
    }

    suspend fun generateUniqueDisplayName(documentType: String, category: String): String {
        val baseName = when {
            documentType == "Vehicle RC" || documentType == "Vehicle Registration Certificate" -> "Vehicle Registration Certificate"
            documentType == "PUC Certificate" -> "PUC Certificate"
            documentType == "Aadhaar Card" -> "Aadhaar Card"
            documentType == "PAN Card" -> "PAN Card"
            documentType == "Driving License" -> "Driving License"
            documentType == "Hall Ticket" -> "Hall Ticket"
            documentType == "Marksheet" -> "Marksheet"
            documentType == "Fee Receipt" -> "Fee Receipt"
            documentType == "Tax Invoice" -> "Tax Invoice"
            documentType == "Salary Slip" -> "Salary Slip"
            documentType == "Scholarship Document" -> "Scholarship Document"
            documentType == "Certificate" || documentType == "College Certificate" -> "Certificate"
            documentType == "College Notice" -> "College Notice"
            documentType == "College Document" -> "College Document"
            documentType.isNotBlank() &&
                    documentType != "Unclassified Document" &&
                    documentType != "Photograph / Non-Document" &&
                    documentType != "Other Document" &&
                    documentType != "Unknown Document" &&
                    category.isNotBlank() &&
                    category != "Other Documents" -> documentType
            else -> "Other Document"
        }

        val existingNames = documentDao.getExistingDisplayNamesStartingWith(baseName)
        if (!existingNames.contains(baseName)) {
            return baseName
        }

        var counter = 2
        while (existingNames.contains("$baseName ($counter)")) {
            counter++
        }
        return "$baseName ($counter)"
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        documentDao.toggleFavorite(id, isFavorite)
    }

    suspend fun deleteDocument(document: DocumentEntity) {
        documentDao.deleteDocument(document)
    }
}
