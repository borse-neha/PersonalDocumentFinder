package com.example.personaldocumentfinder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE contentHash = :hash LIMIT 1")
    suspend fun getDocumentByHash(hash: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE originalUri = :uri LIMIT 1")
    suspend fun getDocumentByUri(uri: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE originalName = :name AND fileSize = :size LIMIT 1")
    suspend fun getDocumentByNameAndSize(name: String, size: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE displayName = :displayName LIMIT 1")
    suspend fun getDocumentByDisplayName(displayName: String): DocumentEntity?

    @Query("SELECT displayName FROM documents WHERE displayName = :name OR displayName LIKE :name || ' (%)'")
    suspend fun getExistingDisplayNamesStartingWith(name: String): List<String>

    @Query("SELECT * FROM documents ORDER BY dateImported DESC")
    fun getAllDocumentsFlow(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE category = :category ORDER BY dateImported DESC")
    fun getDocumentsByCategoryFlow(category: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY dateImported DESC")
    fun getFavoriteDocumentsFlow(): Flow<List<DocumentEntity>>

    @Query("""
        SELECT * FROM documents
        WHERE (
            LOWER(displayName) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(originalName) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(ocrText) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(category) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(documentType) LIKE '%' || LOWER(:query) || '%'
        )
        ORDER BY dateImported DESC
    """)
    fun searchDocumentsFlow(query: String): Flow<List<DocumentEntity>>

    @Query("""
        SELECT * FROM documents
        WHERE (
            (:w1 = '' OR (
                LOWER(displayName) LIKE '%' || LOWER(:w1) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w1) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w1) || '%' OR LOWER(category) LIKE '%' || LOWER(:w1) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w1) || '%'
                OR (:w1_alt != '' AND (LOWER(displayName) LIKE '%' || LOWER(:w1_alt) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w1_alt) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w1_alt) || '%' OR LOWER(category) LIKE '%' || LOWER(:w1_alt) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w1_alt) || '%'))
            ))
            AND
            (:w2 = '' OR (
                LOWER(displayName) LIKE '%' || LOWER(:w2) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w2) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w2) || '%' OR LOWER(category) LIKE '%' || LOWER(:w2) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w2) || '%'
                OR (:w2_alt != '' AND (LOWER(displayName) LIKE '%' || LOWER(:w2_alt) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w2_alt) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w2_alt) || '%' OR LOWER(category) LIKE '%' || LOWER(:w2_alt) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w2_alt) || '%'))
            ))
            AND
            (:w3 = '' OR (
                LOWER(displayName) LIKE '%' || LOWER(:w3) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w3) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w3) || '%' OR LOWER(category) LIKE '%' || LOWER(:w3) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w3) || '%'
                OR (:w3_alt != '' AND (LOWER(displayName) LIKE '%' || LOWER(:w3_alt) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w3_alt) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w3_alt) || '%' OR LOWER(category) LIKE '%' || LOWER(:w3_alt) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w3_alt) || '%'))
            ))
            AND
            (:w4 = '' OR (
                LOWER(displayName) LIKE '%' || LOWER(:w4) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w4) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w4) || '%' OR LOWER(category) LIKE '%' || LOWER(:w4) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w4) || '%'
                OR (:w4_alt != '' AND (LOWER(displayName) LIKE '%' || LOWER(:w4_alt) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w4_alt) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w4_alt) || '%' OR LOWER(category) LIKE '%' || LOWER(:w4_alt) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w4_alt) || '%'))
            ))
            AND
            (:w5 = '' OR (
                LOWER(displayName) LIKE '%' || LOWER(:w5) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w5) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w5) || '%' OR LOWER(category) LIKE '%' || LOWER(:w5) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w5) || '%'
                OR (:w5_alt != '' AND (LOWER(displayName) LIKE '%' || LOWER(:w5_alt) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w5_alt) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w5_alt) || '%' OR LOWER(category) LIKE '%' || LOWER(:w5_alt) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w5_alt) || '%'))
            ))
        )
        ORDER BY dateImported DESC
    """)
    fun searchDocumentsMultiWordFlow(
        w1: String = "",
        w1_alt: String = "",
        w2: String = "",
        w2_alt: String = "",
        w3: String = "",
        w3_alt: String = "",
        w4: String = "",
        w4_alt: String = "",
        w5: String = "",
        w5_alt: String = ""
    ): Flow<List<DocumentEntity>>

    @Query("SELECT COUNT(*) FROM documents WHERE category = :category")
    fun getCountByCategoryFlow(category: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents")
    fun getTotalDocumentCountFlow(): Flow<Int>

    @Query("UPDATE documents SET category = :newCategory, lastModified = :timestamp WHERE id = :id")
    suspend fun updateCategory(id: Long, newCategory: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET displayName = :newDisplayName, lastModified = :timestamp WHERE id = :id")
    suspend fun updateDisplayName(id: Long, newDisplayName: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)
}
