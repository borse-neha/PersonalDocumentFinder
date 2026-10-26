package com.example.personaldocumentfinder.ui;

import android.app.Application;
import android.content.Context;
import androidx.lifecycle.AndroidViewModel;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import com.example.personaldocumentfinder.data.AppDatabase;
import com.example.personaldocumentfinder.data.DocumentDao;
import com.example.personaldocumentfinder.data.DocumentEntity;
import com.example.personaldocumentfinder.data.DocumentRepository;
import com.example.personaldocumentfinder.data.Result;
import com.example.personaldocumentfinder.data.SettingsRepository;
import com.example.personaldocumentfinder.data.Unit;
import com.example.personaldocumentfinder.domain.CandidateImportItem;
import com.example.personaldocumentfinder.domain.CandidateRepository;
import com.example.personaldocumentfinder.domain.ClassificationResult;
import com.example.personaldocumentfinder.domain.DeviceScanner;
import com.example.personaldocumentfinder.domain.DiscoveredCandidate;
import com.example.personaldocumentfinder.domain.DocumentClassifier;
import com.example.personaldocumentfinder.domain.DocumentSearchEngine;
import com.example.personaldocumentfinder.domain.FileOpener;
import com.example.personaldocumentfinder.domain.OcrAnalyzer;
import com.example.personaldocumentfinder.domain.PermissionManager;
import com.example.personaldocumentfinder.domain.PreOcrFilter;
import com.example.personaldocumentfinder.domain.RankedSearchResults;
import com.example.personaldocumentfinder.domain.StorageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DocumentViewModel extends AndroidViewModel {

    private final DocumentRepository repository;
    private final CandidateRepository candidateRepository;
    private final SettingsRepository settingsRepository;
    private final StorageManager storageManager;
    private final DeviceScanner deviceScanner;

    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(4);
    private final AtomicBoolean isScanActive = new AtomicBoolean(false);

    private final MutableLiveData<Boolean> _isDatabaseLoaded = new MutableLiveData<>(false);
    private final LiveData<Boolean> isDatabaseLoaded = _isDatabaseLoaded;

    private final MutableLiveData<String> _searchQuery = new MutableLiveData<>("");
    private final LiveData<String> searchQuery = _searchQuery;

    private final MutableLiveData<Boolean> _isSearchLoading = new MutableLiveData<>(false);
    private final LiveData<Boolean> isSearchLoading = _isSearchLoading;

    private final MediatorLiveData<RankedSearchResults> _rankedSearchResults = new MediatorLiveData<>();
    private final LiveData<RankedSearchResults> rankedSearchResults = _rankedSearchResults;

    private final LiveData<List<DocumentEntity>> searchResults;

    private final MutableLiveData<ImportState> _importState = new MutableLiveData<>(new ImportState.Idle());
    private final LiveData<ImportState> importState = _importState;

    private final MutableLiveData<List<CandidateImportItem>> _candidateQueue = new MutableLiveData<>(Collections.<CandidateImportItem>emptyList());
    private final LiveData<List<CandidateImportItem>> candidateQueue = _candidateQueue;

    private final MutableLiveData<String> _statusMessage = new MutableLiveData<>(null);
    private final LiveData<String> statusMessage = _statusMessage;

    public static abstract class ImportState {
        public static final class Idle extends ImportState {}
        public static final class PermissionRequired extends ImportState {}
        public static final class Processing extends ImportState {
            private final String progress;
            public Processing(String progress) {
                this.progress = progress != null ? progress : "";
            }
            public String getProgress() {
                return progress;
            }
        }
        public static final class ReviewNeeded extends ImportState {
            private final int candidatesCount;
            public ReviewNeeded(int candidatesCount) {
                this.candidatesCount = candidatesCount;
            }
            public int getCandidatesCount() {
                return candidatesCount;
            }
        }
        public static final class Success extends ImportState {
            private final int count;
            public Success(int count) {
                this.count = count;
            }
            public int getCount() {
                return count;
            }
        }
        public static final class Error extends ImportState {
            private final String message;
            public Error(String message) {
                this.message = message != null ? message : "";
            }
            public String getMessage() {
                return message;
            }
        }
    }

    public DocumentViewModel(@NonNull Application application) {
        super(application);
        DocumentDao dao = AppDatabase.getDatabase(application).documentDao();
        repository = new DocumentRepository(dao);
        candidateRepository = new CandidateRepository(repository);
        settingsRepository = new SettingsRepository(application);
        storageManager = new StorageManager(application);
        deviceScanner = new DeviceScanner(application);

        // Setup ranked search results with MediatorLiveData
        _rankedSearchResults.addSource(_searchQuery, new Observer<String>() {
            @Override
            public void onChanged(String query) {
                updateSearchResults(query, repository.getAllDocuments().getValue());
            }
        });
        _rankedSearchResults.addSource(repository.getAllDocuments(), new Observer<List<DocumentEntity>>() {
            @Override
            public void onChanged(List<DocumentEntity> docs) {
                if (Boolean.FALSE.equals(_isDatabaseLoaded.getValue())) {
                    _isDatabaseLoaded.postValue(true);
                }
                updateSearchResults(_searchQuery.getValue(), docs);
            }
        });

        searchResults = Transformations.map(_rankedSearchResults, ranked -> {
            if (ranked == null) return Collections.<DocumentEntity>emptyList();
            return ranked.getAllRanked();
        });

        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                reclassifyExistingDocumentsIfNeeded();
            }
        });
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public LiveData<Boolean> getIsDatabaseLoaded() {
        return isDatabaseLoaded;
    }

    public LiveData<String> getThemeState() {
        return settingsRepository.getThemeLiveData();
    }

    public void setTheme(String theme) {
        settingsRepository.setTheme(theme);
    }

    public LiveData<List<DocumentEntity>> getAllDocuments() {
        return repository.getAllDocuments();
    }

    public LiveData<Map<String, Integer>> getCategoryCounts() {
        return Transformations.map(repository.getAllDocuments(), docs -> {
            Map<String, Integer> counts = new HashMap<>();
            if (docs != null) {
                for (DocumentEntity doc : docs) {
                    String cat = doc.getCategory();
                    if (cat != null && !cat.isEmpty()) {
                        Integer cur = counts.get(cat);
                        counts.put(cat, (cur == null ? 0 : cur) + 1);
                    }
                }
            }
            return counts;
        });
    }

    public LiveData<List<DocumentEntity>> getFavoriteDocuments() {
        return repository.getFavoriteDocuments();
    }

    public LiveData<Integer> getTotalCount() {
        return repository.getTotalCount();
    }

    public LiveData<String> getSearchQuery() {
        return searchQuery;
    }

    public LiveData<Boolean> getIsSearchLoading() {
        return isSearchLoading;
    }

    public LiveData<RankedSearchResults> getRankedSearchResults() {
        return rankedSearchResults;
    }

    public LiveData<List<DocumentEntity>> getSearchResults() {
        return searchResults;
    }

    public LiveData<ImportState> getImportState() {
        return importState;
    }

    public LiveData<List<CandidateImportItem>> getCandidateQueue() {
        return candidateQueue;
    }

    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    public void setSearchQuery(String query) {
        _searchQuery.setValue(query != null ? query : "");
    }

    public void clearStatusMessage() {
        _statusMessage.setValue(null);
    }

    public void clearCandidateQueue() {
        _candidateQueue.postValue(Collections.<CandidateImportItem>emptyList());
        _importState.postValue(new ImportState.Idle());
    }

    public void removeCandidateFromQueue(int index) {
        List<CandidateImportItem> current = _candidateQueue.getValue();
        if (current != null && index >= 0 && index < current.size()) {
            List<CandidateImportItem> mutable = new ArrayList<>(current);
            mutable.remove(index);
            _candidateQueue.setValue(mutable);
            if (mutable.isEmpty()) {
                _importState.setValue(new ImportState.Idle());
            } else {
                _importState.setValue(new ImportState.ReviewNeeded(mutable.size()));
            }
        }
    }

    public LiveData<List<DocumentEntity>> getCategoryDocuments(final String category) {
        return Transformations.map(repository.getAllDocuments(), docs -> {
            if (docs == null) return Collections.<DocumentEntity>emptyList();
            List<DocumentEntity> filtered = new ArrayList<>();
            for (DocumentEntity d : docs) {
                if (d.getCategory() != null && d.getCategory().equals(category)) {
                    filtered.add(d);
                }
            }
            return filtered;
        });
    }

    public LiveData<Integer> getCategoryCount(final String category) {
        return Transformations.map(repository.getAllDocuments(), docs -> {
            if (docs == null) return 0;
            int count = 0;
            for (DocumentEntity d : docs) {
                if (d.getCategory() != null && d.getCategory().equals(category)) {
                    count++;
                }
            }
            return count;
        });
    }

    private void updateSearchResults(final String query, final List<DocumentEntity> allDocs) {
        final String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            _isSearchLoading.postValue(false);
            List<DocumentEntity> list = allDocs != null ? allDocs : Collections.<DocumentEntity>emptyList();
            _rankedSearchResults.postValue(new RankedSearchResults(list, Collections.<DocumentEntity>emptyList()));
        } else {
            _isSearchLoading.postValue(true);
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    List<DocumentEntity> list = allDocs != null ? allDocs : repository.getAllDocumentsSync();
                    RankedSearchResults ranked = DocumentSearchEngine.rankAndGroupDocuments(list, trimmed);
                    _isSearchLoading.postValue(false);
                    _rankedSearchResults.postValue(ranked);
                }
            });
        }
    }

    public void reclassifyExistingDocumentsIfNeeded() {
        try {
            List<DocumentEntity> docs = repository.getAllDocumentsSync();
            if (docs == null) return;
            File baseOrganized = storageManager.getOrganizedBaseDir();

            for (DocumentEntity doc : docs) {
                if (doc == null) continue;

                // 1. Re-run classification
                ClassificationResult classification = DocumentClassifier.classify(doc.getOcrText(), doc.getOriginalName(), doc.getMimeType());
                String targetCategory = classification.getCategory();
                String targetType = classification.getDocumentType();
                boolean categoryChanged = !targetCategory.equals(doc.getCategory());
                boolean typeChanged = !targetType.equals(doc.getDocumentType());

                // 2. Physical storage migration check
                File currentFile = new File(doc.getInternalPath());
                boolean isOutsideOrganized = !currentFile.getAbsolutePath().startsWith(baseOrganized.getAbsolutePath());
                File targetCategoryDir = storageManager.getCategoryDir(targetCategory);
                File parent = currentFile.getParentFile();
                boolean isWrongCategoryFolder = parent == null || !parent.getAbsolutePath().equals(targetCategoryDir.getAbsolutePath());

                String newInternalPath = doc.getInternalPath();
                if (currentFile.exists() && (isOutsideOrganized || isWrongCategoryFolder || categoryChanged)) {
                    String moved = storageManager.moveFileToCategory(doc.getInternalPath(), targetCategory);
                    if (moved != null) {
                        newInternalPath = moved;
                    }
                } else if (!currentFile.exists()) {
                    try {
                        Uri uri = Uri.parse(doc.getOriginalUri());
                        StorageManager.CopyResult copyRes = storageManager.copyFileToOrganizedStorage(uri, targetCategory, doc.getOriginalName());
                        if (copyRes != null) {
                            newInternalPath = copyRes.getInternalPath();
                        }
                    } catch (Exception ignored) {}
                }

                // 3. Update displayName if auto-named or blank
                String displayName = doc.getDisplayName();
                boolean isAutoName = displayName == null || displayName.trim().isEmpty() ||
                        displayName.equals(doc.getOriginalName()) ||
                        displayName.startsWith("PUC") ||
                        displayName.startsWith("Vehicle") ||
                        displayName.startsWith("Aadhaar") ||
                        displayName.startsWith("Fee Receipt") ||
                        displayName.startsWith("Hall Ticket") ||
                        displayName.startsWith("College") ||
                        displayName.startsWith("Government ID") ||
                        displayName.startsWith("Financial Document") ||
                        displayName.startsWith("Other Document");

                String newDisplayName = isAutoName
                        ? repository.generateUniqueDisplayName(targetType, targetCategory)
                        : displayName;

                if (categoryChanged || typeChanged || !newInternalPath.equals(doc.getInternalPath()) || !newDisplayName.equals(displayName)) {
                    DocumentEntity updatedDoc = doc.copy();
                    updatedDoc.setCategory(targetCategory);
                    updatedDoc.setDocumentType(targetType);
                    updatedDoc.setDocumentConfidence(classification.getDocumentConfidence());
                    updatedDoc.setCategoryConfidence(classification.getCategoryConfidence());
                    updatedDoc.setInternalPath(newInternalPath);
                    updatedDoc.setDisplayName(newDisplayName);
                    updatedDoc.setLastModified(System.currentTimeMillis());
                    repository.updateDocument(updatedDoc);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startDeviceScan() {
        if (!PermissionManager.hasStoragePermission(getApplication())) {
            _importState.setValue(new ImportState.PermissionRequired());
            return;
        }

        stopDeviceScan();
        _candidateQueue.setValue(Collections.<CandidateImportItem>emptyList());
        isScanActive.set(true);

        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final int[] counts = new int[5]; // 0: discovered, 1: analyzed, 2: imported, 3: skipped, 4: reviewNeeded
                _importState.postValue(new ImportState.Processing("Scanning storage for candidate documents..."));

                deviceScanner.scanSharedStorage(20, new DeviceScanner.BatchConsumer() {
                    @Override
                    public boolean isCancelled() {
                        return !isScanActive.get();
                    }

                    @Override
                    public void onBatchDiscovered(List<DiscoveredCandidate> batch) {
                        if (!isScanActive.get() || batch == null) return;
                        counts[0] += batch.size();

                        List<DiscoveredCandidate> unimported = candidateRepository.filterUnimportedCandidates(storageManager, batch);
                        for (DiscoveredCandidate candidate : unimported) {
                            if (!isScanActive.get()) return;

                            // 1. Cheap Pre-OCR Filter
                            boolean passesPreOcr = PreOcrFilter.isPlausibleCandidate(
                                    candidate.getName(),
                                    candidate.getMimeType(),
                                    candidate.getFileSize(),
                                    candidate.getFilePath()
                            );

                            if (!passesPreOcr) {
                                counts[3]++;
                                continue;
                            }

                            counts[1]++;
                            _importState.postValue(new ImportState.Processing(
                                    "Scanning: " + counts[0] + " | Analyzing: " + counts[1] + " | OCR: " + candidate.getName() + " | Imported: " + counts[2]
                            ));

                            // 2. OCR Extraction
                            String ocrText = OcrAnalyzer.extractText(getApplication(), candidate.getUri(), candidate.getMimeType());

                            // 3. Document Detection & Multi-Signal Classification
                            ClassificationResult classification = DocumentClassifier.classify(ocrText, candidate.getName(), candidate.getMimeType());

                            if (!classification.isDocument()) {
                                counts[3]++;
                                continue;
                            }

                            // 4. Evidence Confidence Check for Auto Import vs Review
                            boolean isHighConfidence = classification.isDocument() &&
                                    classification.getDocumentConfidence() >= 0.70f &&
                                    classification.getCategoryConfidence() >= 0.70f &&
                                    classification.getDocumentTypeConfidence() >= 0.70f &&
                                    !"Other Documents".equals(classification.getCategory()) &&
                                    !"Other Document".equals(classification.getDocumentType()) &&
                                    !"Unclassified Document".equals(classification.getDocumentType());

                            if (isHighConfidence) {
                                StorageManager.CopyResult copyResult = storageManager.copyFileToOrganizedStorage(
                                        candidate.getUri(),
                                        classification.getCategory(),
                                        candidate.getName()
                                );

                                if (copyResult != null) {
                                    String autoDisplayName = repository.generateUniqueDisplayName(
                                            classification.getDocumentType(),
                                            classification.getCategory()
                                    );

                                    DocumentEntity documentEntity = new DocumentEntity(
                                            0L,
                                            candidate.getName(),
                                            copyResult.getStoredFileName(),
                                            autoDisplayName,
                                            copyResult.getMimeType(),
                                            candidate.getUri().toString(),
                                            copyResult.getInternalPath(),
                                            classification.getCategory(),
                                            classification.getDocumentType(),
                                            classification.getDocumentConfidence(),
                                            classification.getCategoryConfidence(),
                                            classification.getOcrText(),
                                            copyResult.getFileSize(),
                                            System.currentTimeMillis(),
                                            System.currentTimeMillis(),
                                            false,
                                            true,
                                            copyResult.getContentHash()
                                    );

                                    try {
                                        repository.insertDocument(documentEntity);
                                        counts[2]++;
                                    } catch (Exception e) {
                                        storageManager.deleteAppPrivateFile(copyResult.getInternalPath());
                                        e.printStackTrace();
                                    }
                                }
                            } else if (classification.isDocument() && (classification.getCategoryConfidence() >= 0.45f || classification.getDocumentTypeConfidence() >= 0.45f)) {
                                counts[4]++;
                                CandidateImportItem queueItem = new CandidateImportItem(
                                        candidate.getUri(),
                                        candidate.getName(),
                                        classification,
                                        classification.getCategory(),
                                        false
                                );
                                List<CandidateImportItem> current = _candidateQueue.getValue();
                                List<CandidateImportItem> updated = new ArrayList<>(current != null ? current : Collections.<CandidateImportItem>emptyList());
                                updated.add(queueItem);
                                _candidateQueue.postValue(updated);
                            } else if (classification.isDocument()) {
                                StorageManager.CopyResult copyResult = storageManager.copyFileToOrganizedStorage(
                                        candidate.getUri(),
                                        "Other Documents",
                                        candidate.getName()
                                );
                                if (copyResult != null) {
                                    String autoDisplayName = repository.generateUniqueDisplayName(
                                            classification.getDocumentType(),
                                            "Other Documents"
                                    );
                                    DocumentEntity documentEntity = new DocumentEntity(
                                            0L,
                                            candidate.getName(),
                                            copyResult.getStoredFileName(),
                                            autoDisplayName,
                                            copyResult.getMimeType(),
                                            candidate.getUri().toString(),
                                            copyResult.getInternalPath(),
                                            "Other Documents",
                                            classification.getDocumentType(),
                                            classification.getDocumentConfidence(),
                                            classification.getCategoryConfidence(),
                                            classification.getOcrText(),
                                            copyResult.getFileSize(),
                                            System.currentTimeMillis(),
                                            System.currentTimeMillis(),
                                            false,
                                            true,
                                            copyResult.getContentHash()
                                    );
                                    try {
                                        repository.insertDocument(documentEntity);
                                        counts[2]++;
                                    } catch (Exception e) {
                                        storageManager.deleteAppPrivateFile(copyResult.getInternalPath());
                                        e.printStackTrace();
                                    }
                                }
                            } else {
                                counts[3]++;
                            }
                        }
                    }
                });

                if (isScanActive.get()) {
                    isScanActive.set(false);
                    if (counts[4] > 0) {
                        _statusMessage.postValue("Scan complete. Imported " + counts[2] + " document(s), " + counts[4] + " requiring review.");
                        _importState.postValue(new ImportState.ReviewNeeded(counts[4]));
                    } else {
                        _statusMessage.postValue("Scan complete. Imported " + counts[2] + " genuine document(s) (" + counts[3] + " non-documents skipped).");
                        _importState.postValue(new ImportState.Idle());
                    }
                }
            }
        });
    }

    public void stopDeviceScan() {
        if (isScanActive.getAndSet(false)) {
            _importState.postValue(new ImportState.Idle());
            _statusMessage.postValue("Device scan cancelled.");
        }
    }

    public void scanAndAnalyzeDocuments(final List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;

        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                _importState.postValue(new ImportState.Processing("Analyzing " + uris.size() + " candidate file(s)..."));

                final List<CandidateImportItem> candidates = new ArrayList<>();

                for (int index = 0; index < uris.size(); index++) {
                    Uri uri = uris.get(index);
                    _importState.postValue(new ImportState.Processing("Running OCR & classification on file " + (index + 1) + " of " + uris.size() + "..."));

                    try {
                        getApplication().getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (Exception ignored) {}

                    String originalName = storageManager.getFileNameFromUri(uri);
                    String mimeType = getApplication().getContentResolver().getType(uri);
                    if (mimeType == null) mimeType = "application/octet-stream";

                    String ocrText = OcrAnalyzer.extractText(getApplication(), uri, mimeType);
                    ClassificationResult classification = DocumentClassifier.classify(ocrText, originalName, mimeType);

                    String contentHash = storageManager.calculateContentHash(uri);
                    boolean isDuplicate = false;
                    if (contentHash != null && !contentHash.isEmpty()) {
                        isDuplicate = repository.getDocumentByHash(contentHash) != null;
                    }

                    candidates.add(new CandidateImportItem(
                            uri,
                            originalName,
                            classification,
                            classification.getCategory(),
                            isDuplicate
                    ));
                }

                _candidateQueue.postValue(candidates);
                _importState.postValue(new ImportState.ReviewNeeded(candidates.size()));
            }
        });
    }

    public void updateCandidateCategory(int index, String newCategory) {
        List<CandidateImportItem> current = _candidateQueue.getValue();
        if (current != null && index >= 0 && index < current.size()) {
            List<CandidateImportItem> updated = new ArrayList<>(current);
            CandidateImportItem item = updated.get(index);
            updated.set(index, item.copy(newCategory));
            _candidateQueue.setValue(updated);
        }
    }

    public void confirmCandidateImports(final List<CandidateImportItem> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            clearCandidateQueue();
            return;
        }

        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                _importState.postValue(new ImportState.Processing("Importing confirmed documents..."));
                int importedCount = 0;

                for (CandidateImportItem candidate : candidates) {
                    if (candidate == null || candidate.isDuplicate()) continue;

                    StorageManager.CopyResult copyResult = storageManager.copyFileToOrganizedStorage(
                            candidate.getUri(),
                            candidate.getSelectedCategory(),
                            candidate.getFileName()
                    );

                    if (copyResult != null) {
                        String autoDisplayName = repository.generateUniqueDisplayName(
                                candidate.getClassification().getDocumentType(),
                                candidate.getSelectedCategory()
                        );

                        DocumentEntity documentEntity = new DocumentEntity(
                                0L,
                                candidate.getFileName(),
                                copyResult.getStoredFileName(),
                                autoDisplayName,
                                copyResult.getMimeType(),
                                candidate.getUri().toString(),
                                copyResult.getInternalPath(),
                                candidate.getSelectedCategory(),
                                candidate.getClassification().getDocumentType(),
                                candidate.getClassification().getDocumentConfidence(),
                                candidate.getClassification().getCategoryConfidence(),
                                candidate.getClassification().getOcrText(),
                                copyResult.getFileSize(),
                                System.currentTimeMillis(),
                                System.currentTimeMillis(),
                                false,
                                true,
                                copyResult.getContentHash()
                        );

                        try {
                            repository.insertDocument(documentEntity);
                            importedCount++;
                        } catch (Exception e) {
                            storageManager.deleteAppPrivateFile(copyResult.getInternalPath());
                            e.printStackTrace();
                        }
                    }
                }

                clearCandidateQueue();
                _statusMessage.postValue("Imported " + importedCount + " document(s) into library.");
            }
        });
    }

    public void updateDocumentCategory(final DocumentEntity document, final String newCategory) {
        if (document == null) return;
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String newInternalPath = storageManager.moveFileToCategory(document.getInternalPath(), newCategory);
                if (newInternalPath != null) {
                    DocumentEntity updated = document.copy();
                    updated.setCategory(newCategory);
                    updated.setInternalPath(newInternalPath);
                    updated.setLastModified(System.currentTimeMillis());
                    repository.updateDocument(updated);
                }
            }
        });
    }

    public void renameDocument(final DocumentEntity document, final String newDisplayName) {
        if (document == null) return;
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Result<Unit> result = repository.renameDocument(document.getId(), newDisplayName);
                if (result.isFailure()) {
                    Throwable ex = result.exceptionOrNull();
                    _statusMessage.postValue(ex != null && ex.getMessage() != null ? ex.getMessage() : "Rename failed.");
                } else {
                    _statusMessage.postValue("Renamed to '" + newDisplayName + "'.");
                }
            }
        });
    }

    public void toggleFavorite(final DocumentEntity document) {
        if (document == null) return;
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                repository.toggleFavorite(document.getId(), !document.isFavorite());
            }
        });
    }

    public void deleteDocument(final DocumentEntity document) {
        if (document == null) return;
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                storageManager.deleteAppPrivateFile(document.getInternalPath());
                repository.deleteDocument(document);
            }
        });
    }

    public void openDocument(Context context, DocumentEntity document) {
        if (document == null) return;
        FileOpener.OpenResult result = FileOpener.openDocument(context, document.getInternalPath(), document.getMimeType());
        if (result instanceof FileOpener.OpenResult.Error) {
            _statusMessage.setValue(((FileOpener.OpenResult.Error) result).getMessage());
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopDeviceScan();
        ioExecutor.shutdown();
    }
}
