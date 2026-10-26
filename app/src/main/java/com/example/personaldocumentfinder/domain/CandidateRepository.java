package com.example.personaldocumentfinder.domain;

import com.example.personaldocumentfinder.data.DocumentEntity;
import com.example.personaldocumentfinder.data.DocumentRepository;
import java.util.ArrayList;
import java.util.List;

public class CandidateRepository {

    private final DocumentRepository repository;

    public CandidateRepository(DocumentRepository repository) {
        this.repository = repository;
    }

    public List<DiscoveredCandidate> filterUnimportedCandidates(
            StorageManager storageManager,
            List<DiscoveredCandidate> candidates
    ) {
        List<DiscoveredCandidate> unimportedList = new ArrayList<>();
        if (candidates == null) return unimportedList;

        for (DiscoveredCandidate candidate : candidates) {
            if (candidate == null) continue;

            // 1. Metadata check by URI
            if (candidate.getUri() != null) {
                DocumentEntity byUri = repository.getDocumentByUri(candidate.getUri().toString());
                if (byUri != null) continue;
            }

            // 2. Metadata check by Name + Size
            DocumentEntity byNameAndSize = repository.getDocumentByNameAndSize(candidate.getName(), candidate.getFileSize());
            if (byNameAndSize != null) continue;

            // 3. Fast SHA-256 Content Hash Check (catches renamed identical files)
            if (candidate.getUri() != null && storageManager != null) {
                String contentHash = storageManager.calculateContentHash(candidate.getUri());
                if (contentHash != null && !contentHash.isEmpty()) {
                    DocumentEntity byHash = repository.getDocumentByHash(contentHash);
                    if (byHash != null) continue;
                }
            }

            unimportedList.add(candidate);
        }

        return unimportedList;
    }
}
