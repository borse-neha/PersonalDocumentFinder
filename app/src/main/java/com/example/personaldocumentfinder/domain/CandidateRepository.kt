package com.example.personaldocumentfinder.domain

import com.example.personaldocumentfinder.data.DocumentRepository

class CandidateRepository(private val repository: DocumentRepository) {

    suspend fun filterUnimportedCandidates(
        storageManager: StorageManager,
        candidates: List<DiscoveredCandidate>
    ): List<DiscoveredCandidate> {
        val unimportedList = mutableListOf<DiscoveredCandidate>()

        for (candidate in candidates) {
            // 1. Metadata check by URI
            val byUri = repository.getDocumentByUri(candidate.uri.toString())
            if (byUri != null) continue

            // 2. Metadata check by Name + Size
            val byNameAndSize = repository.getDocumentByNameAndSize(candidate.name, candidate.fileSize)
            if (byNameAndSize != null) continue

            // 3. Fast SHA-256 Content Hash Check (catches renamed identical files)
            val contentHash = storageManager.calculateContentHash(candidate.uri)
            if (contentHash != null) {
                val byHash = repository.getDocumentByHash(contentHash)
                if (byHash != null) continue
            }

            unimportedList.add(candidate)
        }

        return unimportedList
    }
}
