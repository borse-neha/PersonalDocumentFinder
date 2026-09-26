package com.example.personaldocumentfinder.data

import kotlinx.coroutines.flow.Flow

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
        return documentDao.searchDocumentsFlow(query)
    }

    suspend fun getDocumentById(id: Long): DocumentEntity? {
        return documentDao.getDocumentById(id)
    }

    suspend fun getDocumentByHash(hash: String): DocumentEntity? {
        return documentDao.getDocumentByHash(hash)
    }

    suspend fun getDocumentByUri(uri: String): DocumentEntity? {
        return documentDao.getDocumentByUri(uri)
    }

    suspend fun getDocumentByNameAndSize(name: String, size: Long): DocumentEntity? {
        return documentDao.getDocumentByNameAndSize(name, size)
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

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        documentDao.toggleFavorite(id, isFavorite)
    }

    suspend fun deleteDocument(document: DocumentEntity) {
        documentDao.deleteDocument(document)
    }
}
