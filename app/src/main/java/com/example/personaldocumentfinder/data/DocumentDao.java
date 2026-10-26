package com.example.personaldocumentfinder.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface DocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertDocument(DocumentEntity document);

    @Update
    void updateDocument(DocumentEntity document);

    @Delete
    void deleteDocument(DocumentEntity document);

    @Query("SELECT * FROM documents WHERE id = :id")
    DocumentEntity getDocumentById(long id);

    @Query("SELECT * FROM documents WHERE contentHash = :hash LIMIT 1")
    DocumentEntity getDocumentByHash(String hash);

    @Query("SELECT * FROM documents WHERE originalUri = :uri LIMIT 1")
    DocumentEntity getDocumentByUri(String uri);

    @Query("SELECT * FROM documents WHERE originalName = :name AND fileSize = :size LIMIT 1")
    DocumentEntity getDocumentByNameAndSize(String name, long size);

    @Query("SELECT * FROM documents WHERE displayName = :displayName LIMIT 1")
    DocumentEntity getDocumentByDisplayName(String displayName);

    @Query("SELECT displayName FROM documents WHERE displayName = :name OR displayName LIKE :name || ' (%)'")
    List<String> getExistingDisplayNamesStartingWith(String name);

    @Query("SELECT * FROM documents ORDER BY dateImported DESC")
    LiveData<List<DocumentEntity>> getAllDocumentsFlow();

    @Query("SELECT * FROM documents ORDER BY dateImported DESC")
    List<DocumentEntity> getAllDocumentsSync();

    @Query("SELECT * FROM documents WHERE category = :category ORDER BY dateImported DESC")
    LiveData<List<DocumentEntity>> getDocumentsByCategoryFlow(String category);

    @Query("SELECT * FROM documents WHERE category = :category ORDER BY dateImported DESC")
    List<DocumentEntity> getDocumentsByCategorySync(String category);

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY dateImported DESC")
    LiveData<List<DocumentEntity>> getFavoriteDocumentsFlow();

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY dateImported DESC")
    List<DocumentEntity> getFavoriteDocumentsSync();

    @Query("SELECT * FROM documents WHERE (" +
            "LOWER(displayName) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(originalName) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(ocrText) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(category) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(documentType) LIKE '%' || LOWER(:query) || '%' " +
            ") ORDER BY dateImported DESC")
    LiveData<List<DocumentEntity>> searchDocumentsFlow(String query);

    @Query("SELECT * FROM documents WHERE (" +
            "(:w1 = '' OR (LOWER(displayName) LIKE '%' || LOWER(:w1) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w1) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w1) || '%' OR LOWER(category) LIKE '%' || LOWER(:w1) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w1) || '%' " +
            "OR (:w1_alt != '' AND (LOWER(displayName) LIKE '%' || LOWER(:w1_alt) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w1_alt) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w1_alt) || '%' OR LOWER(category) LIKE '%' || LOWER(:w1_alt) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w1_alt) || '%')))) " +
            "AND (:w2 = '' OR (LOWER(displayName) LIKE '%' || LOWER(:w2) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w2) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w2) || '%' OR LOWER(category) LIKE '%' || LOWER(:w2) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w2) || '%' " +
            "OR (:w2_alt != '' AND (LOWER(displayName) LIKE '%' || LOWER(:w2_alt) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w2_alt) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w2_alt) || '%' OR LOWER(category) LIKE '%' || LOWER(:w2_alt) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w2_alt) || '%')))) " +
            "AND (:w3 = '' OR (LOWER(displayName) LIKE '%' || LOWER(:w3) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w3) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w3) || '%' OR LOWER(category) LIKE '%' || LOWER(:w3) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w3) || '%' " +
            "OR (:w3_alt != '' AND (LOWER(displayName) LIKE '%' || LOWER(:w3_alt) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w3_alt) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w3_alt) || '%' OR LOWER(category) LIKE '%' || LOWER(:w3_alt) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w3_alt) || '%')))) " +
            "AND (:w4 = '' OR (LOWER(displayName) LIKE '%' || LOWER(:w4) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w4) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w4) || '%' OR LOWER(category) LIKE '%' || LOWER(:w4) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w4) || '%' " +
            "OR (:w4_alt != '' AND (LOWER(displayName) LIKE '%' || LOWER(:w4_alt) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w4_alt) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w4_alt) || '%' OR LOWER(category) LIKE '%' || LOWER(:w4_alt) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w4_alt) || '%')))) " +
            "AND (:w5 = '' OR (LOWER(displayName) LIKE '%' || LOWER(:w5) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w5) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w5) || '%' OR LOWER(category) LIKE '%' || LOWER(:w5) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w5) || '%' " +
            "OR (:w5_alt != '' AND (LOWER(displayName) LIKE '%' || LOWER(:w5_alt) || '%' OR LOWER(originalName) LIKE '%' || LOWER(:w5_alt) || '%' OR LOWER(ocrText) LIKE '%' || LOWER(:w5_alt) || '%' OR LOWER(category) LIKE '%' || LOWER(:w5_alt) || '%' OR LOWER(documentType) LIKE '%' || LOWER(:w5_alt) || '%')))) " +
            ") ORDER BY dateImported DESC")
    LiveData<List<DocumentEntity>> searchDocumentsMultiWordFlow(
            String w1, String w1_alt,
            String w2, String w2_alt,
            String w3, String w3_alt,
            String w4, String w4_alt,
            String w5, String w5_alt
    );

    @Query("SELECT COUNT(*) FROM documents WHERE category = :category")
    LiveData<Integer> getCountByCategoryFlow(String category);

    @Query("SELECT COUNT(*) FROM documents WHERE category = :category")
    int getCountByCategorySync(String category);

    @Query("SELECT COUNT(*) FROM documents")
    LiveData<Integer> getTotalDocumentCountFlow();

    @Query("SELECT COUNT(*) FROM documents")
    int getTotalDocumentCountSync();

    @Query("UPDATE documents SET category = :newCategory, lastModified = :timestamp WHERE id = :id")
    void updateCategory(long id, String newCategory, long timestamp);

    default void updateCategory(long id, String newCategory) {
        updateCategory(id, newCategory, System.currentTimeMillis());
    }

    @Query("UPDATE documents SET displayName = :newDisplayName, lastModified = :timestamp WHERE id = :id")
    void updateDisplayName(long id, String newDisplayName, long timestamp);

    default void updateDisplayName(long id, String newDisplayName) {
        updateDisplayName(id, newDisplayName, System.currentTimeMillis());
    }

    @Query("UPDATE documents SET isFavorite = :isFavorite WHERE id = :id")
    void toggleFavorite(long id, boolean isFavorite);
}
