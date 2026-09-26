package com.example.personaldocumentfinder.domain

import com.example.personaldocumentfinder.data.DocumentRepository

class CandidateRepository(private val repository: DocumentRepository) {

    suspend fun filterUnimportedCandidates(candidates: List<DiscoveredCandidate>): List<DiscoveredCandidate> {
        val unimportedList = mutableListOf<DiscoveredCandidate>()

        for (candidate in candidates) {
            val byUri = repository.getDocumentByUri(candidate.uri.toString())
            if (byUri != null) continue

            val byNameAndSize = repository.getDocumentByNameAndSize(candidate.name, candidate.fileSize)
            if (byNameAndSize != null) continue

            unimportedList.add(candidate)
        }

        return unimportedList
    }
}
