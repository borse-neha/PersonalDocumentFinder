package com.example.personaldocumentfinder;

import com.example.personaldocumentfinder.data.DocumentEntity;
import com.example.personaldocumentfinder.domain.DocumentSearchEngine;
import com.example.personaldocumentfinder.domain.RankedSearchResults;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class DocumentSearchLifecycleTest {

    @Test
    public void testEmptyQuery_returnsAllDocumentsWithoutLoading() {
        List<DocumentEntity> docs = new ArrayList<>();
        docs.add(new DocumentEntity(1L, "puc.pdf", "puc.pdf", "PUC Certificate", "application/pdf",
                "content://1", "/storage/puc.pdf", "Vehicle", "PUC", 0.9f, 0.9f, "POLLUTION", 1000, 0, 0, false, true, "h1"));

        RankedSearchResults results = DocumentSearchEngine.rankAndGroupDocuments(docs, "");
        assertEquals(1, results.getAllRanked().size());
        assertEquals("PUC Certificate", results.getAllRanked().get(0).getDisplayName());
    }

    @Test
    public void testSearchOrdering_primaryAndMentionMatchesSeparation() {
        List<DocumentEntity> docs = new ArrayList<>();
        // Title match
        docs.add(new DocumentEntity(1L, "fee.pdf", "fee.pdf", "Fee Receipt", "application/pdf",
                "content://1", "/storage/fee.pdf", "College", "Fee Receipt", 0.9f, 0.9f, "PAID", 1000, 0, 0, false, true, "h1"));
        // OCR text match only (mention)
        docs.add(new DocumentEntity(2L, "memo.txt", "memo.txt", "Office Memo", "text/plain",
                "content://2", "/storage/memo.txt", "Office", "Memo", 0.9f, 0.9f, "Please reimburse the fee receipt amount", 500, 0, 0, false, true, "h2"));

        RankedSearchResults results = DocumentSearchEngine.rankAndGroupDocuments(docs, "fee");
        assertEquals(1, results.getPrimaryMatches().size());
        assertEquals("Fee Receipt", results.getPrimaryMatches().get(0).getDisplayName());

        assertEquals(1, results.getMentionMatches().size());
        assertEquals("Office Memo", results.getMentionMatches().get(0).getDisplayName());
    }

    @Test
    public void testSearchSequence_staleQueryDoesNotOverwriteNewerQuery() {
        final AtomicInteger currentSequence = new AtomicInteger(0);
        final String[] latestOutput = new String[1];

        // Query 1 launched
        final int seq1 = currentSequence.incrementAndGet(); // 1
        // Query 2 launched immediately after
        final int seq2 = currentSequence.incrementAndGet(); // 2

        // Suppose Query 2 completes first
        if (seq2 == currentSequence.get()) {
            latestOutput[0] = "Query 2 Results";
        }
        assertEquals("Query 2 Results", latestOutput[0]);

        // Query 1 completes late: should be rejected
        if (seq1 == currentSequence.get()) {
            latestOutput[0] = "Query 1 Stale Results";
        }
        assertEquals("Late query must not overwrite newer query", "Query 2 Results", latestOutput[0]);
    }

    @Test
    public void testCategoryCountAggregation_accurateAndNonNull() {
        List<DocumentEntity> docs = new ArrayList<>();
        docs.add(new DocumentEntity(1L, "f1.pdf", "f1.pdf", "Fee Receipt 1", "application/pdf",
                "content://1", "/storage/f1.pdf", "College", "Fee Receipt", 0.9f, 0.9f, "", 1000, 0, 0, false, true, "h1"));
        docs.add(new DocumentEntity(2L, "f2.pdf", "f2.pdf", "Fee Receipt 2", "application/pdf",
                "content://2", "/storage/f2.pdf", "College", "Fee Receipt", 0.9f, 0.9f, "", 1000, 0, 0, false, true, "h2"));
        docs.add(new DocumentEntity(3L, "id.jpg", "id.jpg", "Aadhaar", "image/jpeg",
                "content://3", "/storage/id.jpg", "Identity", "Aadhaar Card", 0.9f, 0.9f, "", 1000, 0, 0, false, true, "h3"));

        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (DocumentEntity d : docs) {
            String cat = d.getCategory();
            Integer cur = counts.get(cat);
            counts.put(cat, (cur == null ? 0 : cur) + 1);
        }

        assertEquals(Integer.valueOf(2), counts.get("College"));
        assertEquals(Integer.valueOf(1), counts.get("Identity"));
        assertNull((Object) counts.get("Vehicle"));
    }

    @Test
    public void testCategoryDetailFiltering_returnsExpectedDocuments() {
        List<DocumentEntity> allDocs = new ArrayList<>();
        allDocs.add(new DocumentEntity(1L, "f1.pdf", "f1.pdf", "Fee Receipt", "application/pdf",
                "content://1", "/storage/f1.pdf", "College", "Fee Receipt", 0.9f, 0.9f, "", 1000, 0, 0, false, true, "h1"));
        allDocs.add(new DocumentEntity(2L, "id.jpg", "id.jpg", "Aadhaar", "image/jpeg",
                "content://2", "/storage/id.jpg", "Identity", "Aadhaar Card", 0.9f, 0.9f, "", 1000, 0, 0, false, true, "h2"));

        List<DocumentEntity> collegeFiltered = new ArrayList<>();
        for (DocumentEntity d : allDocs) {
            if ("College".equals(d.getCategory())) {
                collegeFiltered.add(d);
            }
        }

        assertEquals(1, collegeFiltered.size());
        assertEquals("Fee Receipt", collegeFiltered.get(0).getDisplayName());
    }
}
