package com.example.personaldocumentfinder.data;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.example.personaldocumentfinder.domain.DocumentSearchEngine;
import com.example.personaldocumentfinder.domain.RankedSearchResults;
import java.util.Collections;
import java.util.List;

public class DocumentRepository {

    private final DocumentDao documentDao;

    public DocumentRepository(DocumentDao documentDao) {
        this.documentDao = documentDao;
    }

    public LiveData<List<DocumentEntity>> getAllDocuments() {
        return documentDao.getAllDocumentsFlow();
    }

    public List<DocumentEntity> getAllDocumentsSync() {
        return documentDao.getAllDocumentsSync();
    }

    public LiveData<List<DocumentEntity>> getFavoriteDocuments() {
        return documentDao.getFavoriteDocumentsFlow();
    }

    public List<DocumentEntity> getFavoriteDocumentsSync() {
        return documentDao.getFavoriteDocumentsSync();
    }

    public LiveData<Integer> getTotalCount() {
        return documentDao.getTotalDocumentCountFlow();
    }

    public int getTotalCountSync() {
        return documentDao.getTotalDocumentCountSync();
    }

    public LiveData<List<DocumentEntity>> getDocumentsByCategory(String category) {
        return documentDao.getDocumentsByCategoryFlow(category);
    }

    public List<DocumentEntity> getDocumentsByCategorySync(String category) {
        return documentDao.getDocumentsByCategorySync(category);
    }

    public LiveData<Integer> getCategoryCount(String category) {
        return documentDao.getCountByCategoryFlow(category);
    }

    public int getCategoryCountSync(String category) {
        return documentDao.getCountByCategorySync(category);
    }

    public List<DocumentEntity> searchDocumentsSync(String query) {
        final String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            return getAllDocumentsSync();
        }
        return DocumentSearchEngine.rankAndGroupDocuments(getAllDocumentsSync(), trimmed).getAllRanked();
    }

    public RankedSearchResults searchRankedDocumentsSync(String query) {
        final String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            return new RankedSearchResults(getAllDocumentsSync(), Collections.<DocumentEntity>emptyList());
        }
        return DocumentSearchEngine.rankAndGroupDocuments(getAllDocumentsSync(), trimmed);
    }

    public LiveData<List<DocumentEntity>> searchDocuments(String query) {
        final String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            return getAllDocuments();
        }
        return Transformations.map(getAllDocuments(), candidates -> {
            List<DocumentEntity> list = candidates != null ? candidates : Collections.<DocumentEntity>emptyList();
            return DocumentSearchEngine.rankAndGroupDocuments(list, trimmed).getAllRanked();
        });
    }

    public LiveData<RankedSearchResults> searchRankedDocuments(String query) {
        final String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            return Transformations.map(getAllDocuments(), input ->
                new RankedSearchResults(input != null ? input : Collections.<DocumentEntity>emptyList(), Collections.<DocumentEntity>emptyList())
            );
        }
        return Transformations.map(getAllDocuments(), candidates -> {
            List<DocumentEntity> list = candidates != null ? candidates : Collections.<DocumentEntity>emptyList();
            return DocumentSearchEngine.rankAndGroupDocuments(list, trimmed);
        });
    }

    public DocumentEntity getDocumentById(long id) {
        return documentDao.getDocumentById(id);
    }

    public DocumentEntity getDocumentByUri(String uri) {
        return documentDao.getDocumentByUri(uri);
    }

    public DocumentEntity getDocumentByNameAndSize(String name, long size) {
        return documentDao.getDocumentByNameAndSize(name, size);
    }

    public DocumentEntity getDocumentByHash(String contentHash) {
        return documentDao.getDocumentByHash(contentHash);
    }

    public long insertDocument(DocumentEntity document) {
        return documentDao.insertDocument(document);
    }

    public void updateDocument(DocumentEntity document) {
        documentDao.updateDocument(document);
    }

    public void updateCategory(long id, String newCategory) {
        documentDao.updateCategory(id, newCategory);
    }

    public Result<Unit> renameDocument(long id, String newName) {
        return updateDisplayName(id, newName);
    }

    public Result<Unit> updateDisplayName(long id, String newName) {
        if (newName == null) {
            return Result.failure(new IllegalArgumentException("Document name cannot be empty."));
        }
        String trimmed = newName.trim();
        if (trimmed.isEmpty()) {
            return Result.failure(new IllegalArgumentException("Document name cannot be empty."));
        }
        if (trimmed.length() > 100) {
            return Result.failure(new IllegalArgumentException("Document name cannot exceed 100 characters."));
        }
        DocumentEntity existingDoc = documentDao.getDocumentByDisplayName(trimmed);
        if (existingDoc != null && existingDoc.getId() != id) {
            return Result.failure(new IllegalArgumentException("A document named '" + trimmed + "' already exists."));
        }
        documentDao.updateDisplayName(id, trimmed);
        return Result.success(Unit.INSTANCE);
    }

    public String generateUniqueDisplayName(String documentType, String category) {
        String docType = documentType != null ? documentType : "";
        String cat = category != null ? category : "";

        String baseName;
        if (docType.equals("Vehicle RC") || docType.equals("Vehicle Registration Certificate")) {
            baseName = "Vehicle Registration Certificate";
        } else if (docType.equals("PUC Certificate")) {
            baseName = "PUC Certificate";
        } else if (docType.equals("Aadhaar Card")) {
            baseName = "Aadhaar Card";
        } else if (docType.equals("PAN Card")) {
            baseName = "PAN Card";
        } else if (docType.equals("Driving License")) {
            baseName = "Driving License";
        } else if (docType.equals("Hall Ticket")) {
            baseName = "Hall Ticket";
        } else if (docType.equals("Marksheet")) {
            baseName = "Marksheet";
        } else if (docType.equals("Fee Receipt")) {
            baseName = "Fee Receipt";
        } else if (docType.equals("Tax Invoice")) {
            baseName = "Tax Invoice";
        } else if (docType.equals("Salary Slip")) {
            baseName = "Salary Slip";
        } else if (docType.equals("Scholarship Document")) {
            baseName = "Scholarship Document";
        } else if (docType.equals("Certificate") || docType.equals("College Certificate")) {
            baseName = "Certificate";
        } else if (docType.equals("College Notice")) {
            baseName = "College Notice";
        } else if (docType.equals("College Document")) {
            baseName = "College Document";
        } else if (!docType.trim().isEmpty() &&
                !docType.equals("Unclassified Document") &&
                !docType.equals("Photograph / Non-Document") &&
                !docType.equals("Other Document") &&
                !docType.equals("Unknown Document") &&
                !cat.trim().isEmpty() &&
                !cat.equals("Other Documents")) {
            baseName = docType;
        } else {
            baseName = "Other Document";
        }

        List<String> existingNames = documentDao.getExistingDisplayNamesStartingWith(baseName);
        if (existingNames == null) existingNames = Collections.emptyList();
        if (!existingNames.contains(baseName)) {
            return baseName;
        }

        int counter = 2;
        while (existingNames.contains(baseName + " (" + counter + ")")) {
            counter++;
        }
        return baseName + " (" + counter + ")";
    }

    public void toggleFavorite(long id, boolean isFavorite) {
        documentDao.toggleFavorite(id, isFavorite);
    }

    public void deleteDocument(DocumentEntity document) {
        documentDao.deleteDocument(document);
    }
}
