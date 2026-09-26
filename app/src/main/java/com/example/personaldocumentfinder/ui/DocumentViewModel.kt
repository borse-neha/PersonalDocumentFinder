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
import com.example.personaldocumentfinder.domain.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    private val candidateRepository: CandidateRepository
    private val settingsRepository: com.example.personaldocumentfinder.data.SettingsRepository
    val storageManager: StorageManager
    private val deviceScanner: DeviceScanner

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
        return repository.getCategoryCount(category).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    }

    fun startDeviceScan() {
        if (!PermissionManager.hasStoragePermission(getApplication())) {
            _importState.value = ImportState.PermissionRequired
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _importState.value = ImportState.Processing("Scanning storage for candidate documents...")
            _candidateQueue.value = emptyList()

            val accumulatedCandidates = mutableListOf<CandidateImportItem>()

            deviceScanner.scanSharedStorage(batchSize = 15).collect { batch ->
                val unimported = candidateRepository.filterUnimportedCandidates(batch)

                unimported.forEach { candidate ->
                    _importState.value = ImportState.Processing(
                        "Analyzing content & running OCR (${accumulatedCandidates.size} document candidates found)..."
                    )

                    val ocrText = OcrAnalyzer.extractText(getApplication(), candidate.uri, candidate.mimeType)
                    val classification = DocumentClassifier.classify(ocrText, candidate.name, candidate.mimeType)

                    accumulatedCandidates.add(
                        CandidateImportItem(
                            uri = candidate.uri,
                            fileName = candidate.name,
                            classification = classification,
                            selectedCategory = classification.category,
                            isDuplicate = false
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    _candidateQueue.value = accumulatedCandidates.toList()
                }
            }

            withContext(Dispatchers.Main) {
                if (accumulatedCandidates.isEmpty()) {
                    _statusMessage.value = "Device scan completed. No new unimported documents found."
                    _importState.value = ImportState.Idle
                } else {
                    _importState.value = ImportState.ReviewNeeded(accumulatedCandidates.size)
                }
            }
        }
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
                // Skip rejected non-documents unless user changed assigned category
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
