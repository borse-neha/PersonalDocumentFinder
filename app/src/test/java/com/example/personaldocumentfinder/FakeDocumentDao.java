package com.example.personaldocumentfinder;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.personaldocumentfinder.data.DocumentDao;
import com.example.personaldocumentfinder.data.DocumentEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FakeDocumentDao implements DocumentDao {

    public final List<DocumentEntity> documents = new ArrayList<>();

    @Override
    public long insertDocument(DocumentEntity document) {
        documents.add(document);
        return document.getId();
    }

    @Override
    public void updateDocument(DocumentEntity document) {
        for (int i = 0; i < documents.size(); i++) {
            if (documents.get(i).getId() == document.getId()) {
                documents.set(i, document);
                return;
            }
        }
    }

    @Override
    public void deleteDocument(DocumentEntity document) {
        documents.remove(document);
    }

    @Override
    public DocumentEntity getDocumentById(long id) {
        for (DocumentEntity doc : documents) {
            if (doc.getId() == id) return doc;
        }
        return null;
    }

    @Override
    public DocumentEntity getDocumentByHash(String hash) {
        if (hash == null) return null;
        for (DocumentEntity doc : documents) {
            if (hash.equals(doc.getContentHash())) return doc;
        }
        return null;
    }

    @Override
    public DocumentEntity getDocumentByUri(String uri) {
        if (uri == null) return null;
        for (DocumentEntity doc : documents) {
            if (uri.equals(doc.getOriginalUri())) return doc;
        }
        return null;
    }

    @Override
    public DocumentEntity getDocumentByNameAndSize(String name, long size) {
        if (name == null) return null;
        for (DocumentEntity doc : documents) {
            if (name.equals(doc.getOriginalName()) && doc.getFileSize() == size) {
                return doc;
            }
        }
        return null;
    }

    @Override
    public DocumentEntity getDocumentByDisplayName(String displayName) {
        if (displayName == null) return null;
        for (DocumentEntity doc : documents) {
            if (displayName.equals(doc.getDisplayName())) return doc;
        }
        return null;
    }

    @Override
    public List<String> getExistingDisplayNamesStartingWith(String name) {
        List<String> result = new ArrayList<>();
        if (name == null) return result;
        for (DocumentEntity doc : documents) {
            String dn = doc.getDisplayName();
            if (name.equals(dn) || (dn != null && dn.startsWith(name + " ("))) {
                result.add(dn);
            }
        }
        return result;
    }

    @Override
    public LiveData<List<DocumentEntity>> getAllDocumentsFlow() {
        return new MutableLiveData<>(new ArrayList<>(documents));
    }

    @Override
    public List<DocumentEntity> getAllDocumentsSync() {
        return new ArrayList<>(documents);
    }

    @Override
    public LiveData<List<DocumentEntity>> getDocumentsByCategoryFlow(String category) {
        List<DocumentEntity> filtered = new ArrayList<>();
        for (DocumentEntity doc : documents) {
            if (category != null && category.equals(doc.getCategory())) {
                filtered.add(doc);
            }
        }
        return new MutableLiveData<>(filtered);
    }

    @Override
    public List<DocumentEntity> getDocumentsByCategorySync(String category) {
        List<DocumentEntity> filtered = new ArrayList<>();
        for (DocumentEntity doc : documents) {
            if (category != null && category.equals(doc.getCategory())) {
                filtered.add(doc);
            }
        }
        return filtered;
    }

    @Override
    public LiveData<List<DocumentEntity>> getFavoriteDocumentsFlow() {
        List<DocumentEntity> filtered = new ArrayList<>();
        for (DocumentEntity doc : documents) {
            if (doc.isFavorite()) {
                filtered.add(doc);
            }
        }
        return new MutableLiveData<>(filtered);
    }

    @Override
    public List<DocumentEntity> getFavoriteDocumentsSync() {
        List<DocumentEntity> filtered = new ArrayList<>();
        for (DocumentEntity doc : documents) {
            if (doc.isFavorite()) {
                filtered.add(doc);
            }
        }
        return filtered;
    }

    @Override
    public LiveData<List<DocumentEntity>> searchDocumentsFlow(String query) {
        String lower = query != null ? query.toLowerCase().trim() : "";
        List<DocumentEntity> filtered = new ArrayList<>();
        for (DocumentEntity doc : documents) {
            if (lower.isEmpty() ||
                    (doc.getDisplayName() != null && doc.getDisplayName().toLowerCase().contains(lower)) ||
                    (doc.getOriginalName() != null && doc.getOriginalName().toLowerCase().contains(lower)) ||
                    (doc.getOcrText() != null && doc.getOcrText().toLowerCase().contains(lower)) ||
                    (doc.getCategory() != null && doc.getCategory().toLowerCase().contains(lower)) ||
                    (doc.getDocumentType() != null && doc.getDocumentType().toLowerCase().contains(lower))) {
                filtered.add(doc);
            }
        }
        return new MutableLiveData<>(filtered);
    }

    @Override
    public LiveData<List<DocumentEntity>> searchDocumentsMultiWordFlow(
            String w1, String w1_alt,
            String w2, String w2_alt,
            String w3, String w3_alt,
            String w4, String w4_alt,
            String w5, String w5_alt
    ) {
        List<List<String>> pairs = new ArrayList<>();
        addPairIfNotEmpty(pairs, w1, w1_alt);
        addPairIfNotEmpty(pairs, w2, w2_alt);
        addPairIfNotEmpty(pairs, w3, w3_alt);
        addPairIfNotEmpty(pairs, w4, w4_alt);
        addPairIfNotEmpty(pairs, w5, w5_alt);

        List<DocumentEntity> filtered = new ArrayList<>();
        for (DocumentEntity doc : documents) {
            if (pairs.isEmpty()) {
                filtered.add(doc);
                continue;
            }
            List<String> fields = Arrays.asList(
                    (doc.getDisplayName() != null ? doc.getDisplayName() : "").toLowerCase(),
                    (doc.getOriginalName() != null ? doc.getOriginalName() : "").toLowerCase(),
                    (doc.getOcrText() != null ? doc.getOcrText() : "").toLowerCase(),
                    (doc.getCategory() != null ? doc.getCategory() : "").toLowerCase(),
                    (doc.getDocumentType() != null ? doc.getDocumentType() : "").toLowerCase()
            );

            boolean matchesAll = true;
            for (List<String> variants : pairs) {
                boolean variantMatched = false;
                for (String variant : variants) {
                    for (String field : fields) {
                        if (field.contains(variant)) {
                            variantMatched = true;
                            break;
                        }
                    }
                    if (variantMatched) break;
                }
                if (!variantMatched) {
                    matchesAll = false;
                    break;
                }
            }
            if (matchesAll) {
                filtered.add(doc);
            }
        }
        return new MutableLiveData<>(filtered);
    }

    private void addPairIfNotEmpty(List<List<String>> pairs, String w, String w_alt) {
        if (w != null && !w.trim().isEmpty()) {
            List<String> list = new ArrayList<>();
            list.add(w.toLowerCase().trim());
            if (w_alt != null && !w_alt.trim().isEmpty()) {
                list.add(w_alt.toLowerCase().trim());
            }
            pairs.add(list);
        }
    }

    @Override
    public LiveData<Integer> getCountByCategoryFlow(String category) {
        int count = 0;
        for (DocumentEntity doc : documents) {
            if (category != null && category.equals(doc.getCategory())) count++;
        }
        return new MutableLiveData<>(count);
    }

    @Override
    public int getCountByCategorySync(String category) {
        int count = 0;
        for (DocumentEntity doc : documents) {
            if (category != null && category.equals(doc.getCategory())) count++;
        }
        return count;
    }

    @Override
    public LiveData<Integer> getTotalDocumentCountFlow() {
        return new MutableLiveData<>(documents.size());
    }

    @Override
    public int getTotalDocumentCountSync() {
        return documents.size();
    }

    @Override
    public void updateCategory(long id, String newCategory, long timestamp) {
        for (DocumentEntity doc : documents) {
            if (doc.getId() == id) {
                doc.setCategory(newCategory);
                doc.setLastModified(timestamp);
                break;
            }
        }
    }

    @Override
    public void updateDisplayName(long id, String newDisplayName, long timestamp) {
        for (DocumentEntity doc : documents) {
            if (doc.getId() == id) {
                doc.setDisplayName(newDisplayName);
                doc.setLastModified(timestamp);
                break;
            }
        }
    }

    @Override
    public void toggleFavorite(long id, boolean isFavorite) {
        for (DocumentEntity doc : documents) {
            if (doc.getId() == id) {
                doc.setFavorite(isFavorite);
                break;
            }
        }
    }
}
