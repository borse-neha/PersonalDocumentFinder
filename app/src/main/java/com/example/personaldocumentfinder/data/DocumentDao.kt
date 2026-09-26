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

    @Query("SELECT * FROM documents ORDER BY dateImported DESC")
    fun getAllDocumentsFlow(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE category = :category ORDER BY dateImported DESC")
    fun getDocumentsByCategoryFlow(category: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY dateImported DESC")
    fun getFavoriteDocumentsFlow(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE originalName LIKE '%' || :query || '%' OR ocrText LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR documentType LIKE '%' || :query || '%' ORDER BY dateImported DESC")
    fun searchDocumentsFlow(query: String): Flow<List<DocumentEntity>>

    @Query("SELECT COUNT(*) FROM documents WHERE category = :category")
    fun getCountByCategoryFlow(category: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents")
    fun getTotalDocumentCountFlow(): Flow<Int>

    @Query("UPDATE documents SET category = :newCategory, lastModified = :timestamp WHERE id = :id")
    suspend fun updateCategory(id: Long, newCategory: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)
}
