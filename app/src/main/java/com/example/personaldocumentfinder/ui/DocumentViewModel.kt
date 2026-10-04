package com.example.personaldocumentfinder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.personaldocumentfinder.data.AppDatabase
import com.example.personaldocumentfinder.data.DocumentEntity
import com.example.personaldocumentfinder.data.DocumentRepository
import com.example.personaldocumentfinder.domain.CandidateImportItem
import com.example.personaldocumentfinder.domain.CandidateRepository
import com.example.personaldocumentfinder.domain.DeviceScanner
import com.example.personaldocumentfinder.domain.DocumentClassifier
import com.example.personaldocumentfinder.domain.FileOpener
import com.example.personaldocumentfinder.domain.OcrAnalyzer
import com.example.personaldocumentfinder.domain.PermissionManager
import com.example.personaldocumentfinder.domain.PreOcrFilter
import com.example.personaldocumentfinder.domain.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    private val candidateRepository: CandidateRepository
    private val settingsRepository: com.example.personaldocumentfinder.data.SettingsRepository
    val storageManager: StorageManager
    private val deviceScanner: DeviceScanner

    private var activeScanJob: Job? = null

    init {
        val dao = AppDatabase.getDatabase(application).documentDao()
        repository = DocumentRepository(dao)
        candidateRepository = CandidateRepository(repository)
        settingsRepository = com.example.personaldocumentfinder.data.SettingsRepository(application)
        storageManager = StorageManager(application)
        deviceScanner = DeviceScanner(application)
    }

    val themeState: StateFlow<String> = settingsRepository.themeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "System"
    )

    fun setTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
        }
    }

    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Persistent Map of category counts derived from allDocuments to prevent temporary 0 count flicker
    val categoryCounts: StateFlow<Map<String, Int>> = allDocuments
        .map { docs -> docs.groupingBy { it.category }.eachCount() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val favoriteDocuments: StateFlow<List<DocumentEntity>> = repository.favoriteDocuments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalCount: StateFlow<Int> = repository.totalCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<DocumentEntity>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            repository.allDocuments
        } else {
            repository.searchDocuments(query.trim())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _candidateQueue = MutableStateFlow<List<CandidateImportItem>>(emptyList())
    val candidateQueue: StateFlow<List<CandidateImportItem>> = _candidateQueue.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    sealed class ImportState {
        object Idle : ImportState()
        object PermissionRequired : ImportState()
        data class Processing(val progress: String) : ImportState()
        data class ReviewNeeded(val candidatesCount: Int) : ImportState()
        data class Success(val count: Int) : ImportState()
        data class Error(val message: String) : ImportState()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearCandidateQueue() {
        _candidateQueue.value = emptyList()
        _importState.value = ImportState.Idle
    }

    fun getCategoryDocuments(category: String): StateFlow<List<DocumentEntity>> {
        return repository.getDocumentsByCategory(category).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun getCategoryCount(category: String): StateFlow<Int> {
        return allDocuments
            .map { docs -> docs.count { it.category == category } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = categoryCounts.value[category] ?: 0
            )
    }

    fun startDeviceScan() {
        if (!PermissionManager.hasStoragePermission(getApplication())) {
            _importState.value = ImportState.PermissionRequired
            return
        }

        activeScanJob?.cancel()
        _candidateQueue.value = emptyList()

        activeScanJob = viewModelScope.launch(Dispatchers.IO) {
            var discoveredCount = 0
            var analyzedCount = 0
            var importedCount = 0
            var skippedNonDocsCount = 0
            var reviewNeededCount = 0

            _importState.value = ImportState.Processing("Scanning storage for candidate documents...")

            deviceScanner.scanSharedStorage(batchSize = 20).collect { batch ->
                if (!isActive) return@collect

                discoveredCount += batch.size
                val unimported = candidateRepository.filterUnimportedCandidates(storageManager, batch)

                unimported.forEach { candidate ->
                    if (!isActive) return@forEach

                    // 1. Cheap Pre-OCR Filter
                    val passesPreOcr = PreOcrFilter.isPlausibleCandidate(
                        candidate.name,
                        candidate.mimeType,
                        candidate.fileSize,
                        candidate.filePath
                    )

                    if (!passesPreOcr) {
                        skippedNonDocsCount++
                        return@forEach
                    }

                    analyzedCount++
                    _importState.value = ImportState.Processing(
                        "Discovered: $discoveredCount | Analyzing: $analyzedCount | Imported: $importedCount"
                    )

                    // 2. OCR Extraction
                    val ocrText = OcrAnalyzer.extractText(getApplication(), candidate.uri, candidate.mimeType)

                    // 3. Document Detection & Classification
                    val classification = DocumentClassifier.classify(ocrText, candidate.name, candidate.mimeType)

                    if (!classification.isDocument) {
                        skippedNonDocsCount++
                        return@forEach
                    }

                    // 4. Real-Time Incremental Auto Import for High-Confidence Documents
                    val isHighConfidenceCategory = classification.category != "Other Documents" && classification.categoryConfidence >= 0.5f

                    if (isHighConfidenceCategory) {
                        val copyResult = storageManager.copyFileToAppPrivateStorage(
                            uri = candidate.uri,
                            category = classification.category,
                            customFileName = candidate.name
                        )

                        if (copyResult != null) {
                            val documentEntity = DocumentEntity(
                                originalName = candidate.name,
                                storedFileName = copyResult.storedFileName,
                                mimeType = copyResult.mimeType,
                                originalUri = candidate.uri.toString(),
                                internalPath = copyResult.internalPath,
                                category = classification.category,
                                documentType = classification.documentType,
                                documentConfidence = classification.documentConfidence,
                                categoryConfidence = classification.categoryConfidence,
                                ocrText = classification.ocrText,
                                fileSize = copyResult.fileSize,
                                dateImported = System.currentTimeMillis(),
                                lastModified = System.currentTimeMillis(),
                                isFavorite = false,
                                isReviewed = true,
                                contentHash = copyResult.contentHash
                            )

                            repository.insertDocument(documentEntity)
                            importedCount++
                        }
                    } else {
                        reviewNeededCount++
                        val queueItem = CandidateImportItem(
                            uri = candidate.uri,
                            fileName = candidate.name,
                            classification = classification,
                            selectedCategory = classification.category,
                            isDuplicate = false
                        )
                        val currentQueue = _candidateQueue.value.toMutableList()
                        currentQueue.add(queueItem)
                        _candidateQueue.value = currentQueue
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (reviewNeededCount > 0) {
                    _statusMessage.value = "Scan complete. Imported $importedCount document(s), $reviewNeededCount requiring category review."
                    _importState.value = ImportState.ReviewNeeded(reviewNeededCount)
                } else {
                    _statusMessage.value = "Scan complete. Imported $importedCount genuine document(s) ($skippedNonDocsCount non-documents skipped)."
                    _importState.value = ImportState.Idle
                }
            }
        }
    }

    fun stopDeviceScan() {
        activeScanJob?.cancel()
        activeScanJob = null
        _importState.value = ImportState.Idle
        _statusMessage.value = "Device scan cancelled."
    }

    fun scanAndAnalyzeDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _importState.value = ImportState.Processing("Analyzing ${uris.size} candidate file(s)...")

            val candidates = mutableListOf<CandidateImportItem>()

            uris.forEachIndexed { index, uri ->
                _importState.value = ImportState.Processing("Running OCR & classification on file ${index + 1} of ${uris.size}...")

                try {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                val originalName = storageManager.getFileNameFromUri(uri)
                val mimeType = getApplication<Application>().contentResolver.getType(uri) ?: "application/octet-stream"

                val ocrText = OcrAnalyzer.extractText(getApplication(), uri, mimeType)
                val classification = DocumentClassifier.classify(ocrText, originalName, mimeType)

                val copyResult = storageManager.copyFileToAppPrivateStorage(uri, classification.category, originalName)
                var isDuplicate = false
                if (copyResult != null) {
                    val existingDuplicate = repository.getDocumentByHash(copyResult.contentHash)
                    if (existingDuplicate != null) {
                        isDuplicate = true
                    }
                    storageManager.deleteAppPrivateFile(copyResult.internalPath)
                }

                candidates.add(
                    CandidateImportItem(
                        uri = uri,
                        fileName = originalName,
                        classification = classification,
                        selectedCategory = classification.category,
                        isDuplicate = isDuplicate
                    )
                )
            }

            withContext(Dispatchers.Main) {
                _candidateQueue.value = candidates
                _importState.value = ImportState.ReviewNeeded(candidates.size)
            }
        }
    }

    fun updateCandidateCategory(index: Int, newCategory: String) {
        val currentList = _candidateQueue.value.toMutableList()
        if (index in currentList.indices) {
            val item = currentList[index]
            currentList[index] = item.copy(selectedCategory = newCategory)
            _candidateQueue.value = currentList
        }
    }

    fun confirmCandidateImports(candidates: List<CandidateImportItem>, deleteOriginals: Boolean = false) {
        if (candidates.isEmpty()) {
            clearCandidateQueue()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _importState.value = ImportState.Processing("Importing ${candidates.size} document(s)...")

            var importedCount = 0
            var duplicateCount = 0
            var deletedOriginalsCount = 0

            candidates.forEach { candidate ->
                if (!candidate.classification.isDocument && candidate.selectedCategory == "Other Documents") {
                    return@forEach
                }

                val copyResult = storageManager.copyFileToAppPrivateStorage(
                    uri = candidate.uri,
                    category = candidate.selectedCategory,
                    customFileName = candidate.fileName
                )

                if (copyResult != null) {
                    if (candidate.isDuplicate) duplicateCount++

                    val documentEntity = DocumentEntity(
                        originalName = candidate.fileName,
                        storedFileName = copyResult.storedFileName,
                        mimeType = copyResult.mimeType,
                        originalUri = candidate.uri.toString(),
                        internalPath = copyResult.internalPath,
                        category = candidate.selectedCategory,
                        documentType = candidate.classification.documentType,
                        documentConfidence = candidate.classification.documentConfidence,
                        categoryConfidence = candidate.classification.categoryConfidence,
                        ocrText = candidate.classification.ocrText,
                        fileSize = copyResult.fileSize,
                        dateImported = System.currentTimeMillis(),
                        lastModified = System.currentTimeMillis(),
                        isFavorite = false,
                        isReviewed = true,
                        contentHash = copyResult.contentHash
                    )

                    repository.insertDocument(documentEntity)
                    importedCount++

                    if (deleteOriginals) {
                        try {
                            val deleted = getApplication<Application>().contentResolver.delete(candidate.uri, null, null) > 0
                            if (deleted) deletedOriginalsCount++
                        } catch (_: Exception) {}
                    }
                }
            }

            withContext(Dispatchers.Main) {
                val deleteMsg = if (deletedOriginalsCount > 0) " ($deletedOriginalsCount original file(s) removed)" else ""
                _statusMessage.value = if (duplicateCount > 0) {
                    "Imported $importedCount document(s) ($duplicateCount duplicate content detected)$deleteMsg."
                } else {
                    "Imported $importedCount document(s) with OCR analysis$deleteMsg."
                }
                clearCandidateQueue()
            }
        }
    }

    fun updateDocumentCategory(document: DocumentEntity, newCategory: String) {
        if (document.category == newCategory) return

        viewModelScope.launch(Dispatchers.IO) {
            val newPath = storageManager.moveFileToCategory(document.internalPath, newCategory)
            if (newPath != null) {
                val updated = document.copy(
                    category = newCategory,
                    internalPath = newPath,
                    lastModified = System.currentTimeMillis()
                )
                repository.updateDocument(updated)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Moved to $newCategory"
                }
            } else {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to update category"
                }
            }
        }
    }

    fun toggleFavorite(document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(document.id, !document.isFavorite)
        }
    }

    fun deleteDocument(document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.deleteAppPrivateFile(document.internalPath)
            repository.deleteDocument(document)
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Removed document from app"
            }
        }
    }

    fun openDocument(context: android.content.Context, document: DocumentEntity) {
        val result = FileOpener.openDocument(context, document.internalPath, document.mimeType)
        if (result is FileOpener.OpenResult.Error) {
            _statusMessage.value = result.message
        }
    }
}
