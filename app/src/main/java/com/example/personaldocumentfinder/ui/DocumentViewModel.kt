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
import com.example.personaldocumentfinder.domain.RankedSearchResults
import com.example.personaldocumentfinder.domain.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    private val candidateRepository: CandidateRepository
    private val settingsRepository: com.example.personaldocumentfinder.data.SettingsRepository
    val storageManager: StorageManager
    private val deviceScanner: DeviceScanner

    private var activeScanJob: Job? = null

    private val _isDatabaseLoaded = MutableStateFlow(false)
    val isDatabaseLoaded: StateFlow<Boolean> = _isDatabaseLoaded.asStateFlow()

    init {
        val dao = AppDatabase.getDatabase(application).documentDao()
        repository = DocumentRepository(dao)
        candidateRepository = CandidateRepository(repository)
        settingsRepository = com.example.personaldocumentfinder.data.SettingsRepository(application)
        storageManager = StorageManager(application)
        deviceScanner = DeviceScanner(application)

        viewModelScope.launch(Dispatchers.IO) {
            repository.allDocuments.collect {
                _isDatabaseLoaded.value = true
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            reclassifyExistingDocumentsIfNeeded()
        }
    }

    suspend fun reclassifyExistingDocumentsIfNeeded() {
        try {
            val docs = repository.allDocuments.first()
            val baseOrganized = storageManager.getOrganizedBaseDir()
            for (doc in docs) {
                // 1. Re-run classification
                val classification = DocumentClassifier.classify(doc.ocrText, doc.originalName, doc.mimeType)
                val targetCategory = classification.category
                val targetType = classification.documentType
                val categoryChanged = doc.category != targetCategory
                val typeChanged = doc.documentType != targetType

                // 2. Physical storage migration check
                val currentFile = File(doc.internalPath)
                val isOutsideOrganized = !currentFile.absolutePath.startsWith(baseOrganized.absolutePath)
                val targetCategoryDir = storageManager.getCategoryDir(targetCategory)
                val isWrongCategoryFolder = !currentFile.parentFile?.absolutePath.equals(targetCategoryDir.absolutePath)

                var newInternalPath = doc.internalPath
                if (currentFile.exists() && (isOutsideOrganized || isWrongCategoryFolder || categoryChanged)) {
                    val moved = storageManager.moveFileToCategory(doc.internalPath, targetCategory)
                    if (moved != null) {
                        newInternalPath = moved
                    }
                } else if (!currentFile.exists()) {
                    // Try copying from originalUri if available
                    try {
                        val uri = Uri.parse(doc.originalUri)
                        val copyRes = storageManager.copyFileToOrganizedStorage(uri, targetCategory, doc.originalName)
                        if (copyRes != null) {
                            newInternalPath = copyRes.internalPath
                        }
                    } catch (_: Exception) {}
                }

                // 3. Update displayName if auto-named or blank
                val isAutoName = doc.displayName.isBlank() ||
                        doc.displayName == doc.originalName ||
                        doc.displayName.startsWith("PUC") ||
                        doc.displayName.startsWith("Vehicle") ||
                        doc.displayName.startsWith("Aadhaar") ||
                        doc.displayName.startsWith("Fee Receipt") ||
                        doc.displayName.startsWith("Hall Ticket") ||
                        doc.displayName.startsWith("College") ||
                        doc.displayName.startsWith("Government ID") ||
                        doc.displayName.startsWith("Financial Document") ||
                        doc.displayName.startsWith("Other Document")

                val newDisplayName = if (isAutoName) {
                    repository.generateUniqueDisplayName(targetType, targetCategory)
                } else {
                    doc.displayName
                }

                if (categoryChanged || typeChanged || newInternalPath != doc.internalPath || newDisplayName != doc.displayName) {
                    val updatedDoc = doc.copy(
                        category = targetCategory,
                        documentType = targetType,
                        documentConfidence = classification.documentConfidence,
                        categoryConfidence = classification.categoryConfidence,
                        internalPath = newInternalPath,
                        displayName = newDisplayName,
                        lastModified = System.currentTimeMillis()
                    )
                    repository.updateDocument(updatedDoc)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val categoryCounts: StateFlow<Map<String, Int>> = allDocuments
        .map { docs -> docs.groupingBy { it.category }.eachCount() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap()
        )

    val favoriteDocuments: StateFlow<List<DocumentEntity>> = repository.favoriteDocuments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val totalCount: StateFlow<Int> = allDocuments
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    val rankedSearchResults: StateFlow<RankedSearchResults> = _searchQuery.flatMapLatest { query ->
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _isSearchLoading.value = false
            repository.allDocuments.map { list ->
                RankedSearchResults(primaryMatches = list, mentionMatches = emptyList())
            }
        } else {
            _isSearchLoading.value = true
            repository.searchRankedDocuments(trimmed).map { ranked ->
                _isSearchLoading.value = false
                ranked
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = RankedSearchResults(emptyList(), emptyList())
    )

    val searchResults: StateFlow<List<DocumentEntity>> = rankedSearchResults
        .map { it.primaryMatches + it.mentionMatches }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
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
        return allDocuments
            .map { docs -> docs.filter { it.category == category } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = allDocuments.value.filter { it.category == category }
            )
    }

    fun getCategoryCount(category: String): StateFlow<Int> {
        return allDocuments
            .map { docs -> docs.count { it.category == category } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = allDocuments.value.count { it.category == category }
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
                        "Scanning: $discoveredCount | Analyzing: $analyzedCount | OCR: ${candidate.name} | Imported: $importedCount"
                    )

                    // 2. OCR Extraction
                    val ocrText = OcrAnalyzer.extractText(getApplication(), candidate.uri, candidate.mimeType)

                    // 3. Document Detection & Multi-Signal Classification
                    val classification = DocumentClassifier.classify(ocrText, candidate.name, candidate.mimeType)

                    if (!classification.isDocument) {
                        skippedNonDocsCount++
                        return@forEach
                    }

                    // 4. Evidence Confidence Check for Auto Import vs Review
                    val isHighConfidence = classification.isDocument &&
                            classification.documentConfidence >= 0.70f &&
                            classification.categoryConfidence >= 0.70f &&
                            classification.documentTypeConfidence >= 0.70f &&
                            classification.category != "Other Documents" &&
                            classification.documentType != "Other Document" &&
                            classification.documentType != "Unclassified Document"

                    if (isHighConfidence) {
                        val copyResult = storageManager.copyFileToOrganizedStorage(
                            uri = candidate.uri,
                            category = classification.category,
                            customFileName = candidate.name
                        )

                        if (copyResult != null) {
                            val autoDisplayName = repository.generateUniqueDisplayName(
                                classification.documentType,
                                classification.category
                            )

                            val documentEntity = DocumentEntity(
                                originalName = candidate.name,
                                storedFileName = copyResult.storedFileName,
                                displayName = autoDisplayName,
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
                    } else if (classification.isDocument && (classification.categoryConfidence >= 0.45f || classification.documentTypeConfidence >= 0.45f)) {
                        // Candidate with probable evidence goes into Review Queue
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
                    } else if (classification.isDocument) {
                        // Low confidence generic document
                        val copyResult = storageManager.copyFileToOrganizedStorage(
                            uri = candidate.uri,
                            category = "Other Documents",
                            customFileName = candidate.name
                        )
                        if (copyResult != null) {
                            val autoDisplayName = repository.generateUniqueDisplayName(
                                classification.documentType,
                                "Other Documents"
                            )
                            val documentEntity = DocumentEntity(
                                originalName = candidate.name,
                                storedFileName = copyResult.storedFileName,
                                displayName = autoDisplayName,
                                mimeType = copyResult.mimeType,
                                originalUri = candidate.uri.toString(),
                                internalPath = copyResult.internalPath,
                                category = "Other Documents",
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
                        skippedNonDocsCount++
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (reviewNeededCount > 0) {
                    _statusMessage.value = "Scan complete. Imported $importedCount document(s), $reviewNeededCount requiring review."
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

                val copyResult = storageManager.copyFileToOrganizedStorage(uri, classification.category, originalName)
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
            _importState.value = ImportState.Processing("Importing confirmed documents...")
            var importedCount = 0

            candidates.forEach { candidate ->
                if (candidate.isDuplicate) return@forEach

                val copyResult = storageManager.copyFileToOrganizedStorage(
                    uri = candidate.uri,
                    category = candidate.selectedCategory,
                    customFileName = candidate.fileName
                )

                if (copyResult != null) {
                    val autoDisplayName = repository.generateUniqueDisplayName(
                        candidate.classification.documentType,
                        candidate.selectedCategory
                    )

                    val documentEntity = DocumentEntity(
                        originalName = candidate.fileName,
                        storedFileName = copyResult.storedFileName,
                        displayName = autoDisplayName,
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
                }
            }

            withContext(Dispatchers.Main) {
                clearCandidateQueue()
                _statusMessage.value = "Imported $importedCount document(s) into library."
            }
        }
    }

    fun updateDocumentCategory(document: DocumentEntity, newCategory: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newInternalPath = storageManager.moveFileToCategory(document.internalPath, newCategory)
            if (newInternalPath != null) {
                val updated = document.copy(
                    category = newCategory,
                    internalPath = newInternalPath,
                    lastModified = System.currentTimeMillis()
                )
                repository.updateDocument(updated)
            }
        }
    }

    fun renameDocument(document: DocumentEntity, newDisplayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.renameDocument(document.id, newDisplayName)
            if (result.isFailure) {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Rename failed."
            } else {
                _statusMessage.value = "Renamed to '$newDisplayName'."
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
        }
    }

    fun openDocument(context: android.content.Context, document: DocumentEntity) {
        val result = FileOpener.openDocument(context, document.internalPath, document.mimeType)
        if (result is FileOpener.OpenResult.Error) {
            _statusMessage.value = result.message
        }
    }
}
