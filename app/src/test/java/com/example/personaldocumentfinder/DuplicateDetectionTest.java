package com.example.personaldocumentfinder;

import android.content.ContextWrapper;
import android.net.Uri;
import com.example.personaldocumentfinder.data.DocumentEntity;
import com.example.personaldocumentfinder.data.DocumentRepository;
import com.example.personaldocumentfinder.domain.CandidateRepository;
import com.example.personaldocumentfinder.domain.DiscoveredCandidate;
import com.example.personaldocumentfinder.domain.StorageManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class DuplicateDetectionTest {

    private FakeDocumentDao fakeDao;
    private DocumentRepository repository;
    private CandidateRepository candidateRepository;

    private static class StubContext extends ContextWrapper {
        public StubContext() {
            super(null);
        }
    }

    private static class TestStorageManager extends StorageManager {
        String stubHash = "sha256_hash_abc123";

        public TestStorageManager() {
            super(new StubContext());
        }

        @Override
        public String calculateContentHash(Uri uri) {
            return stubHash;
        }
    }

    @Before
    public void setUp() {
        fakeDao = new FakeDocumentDao();
        repository = new DocumentRepository(fakeDao);
        candidateRepository = new CandidateRepository(repository);
    }

    @Test
    public void testUnimportedCandidatePassesFilter() {
        Uri uri = Uri.parse("content://media/external/file/101");
        DiscoveredCandidate candidate = new DiscoveredCandidate(
                uri,
                "College_HallTicket.pdf",
                "application/pdf",
                102400L,
                "/storage/emulated/0/Download/College_HallTicket.pdf",
                System.currentTimeMillis()
        );

        TestStorageManager testStorageManager = new TestStorageManager();
        testStorageManager.stubHash = "sha256_hash_abc123";

        List<DiscoveredCandidate> filtered = candidateRepository.filterUnimportedCandidates(testStorageManager, Collections.singletonList(candidate));

        assertEquals(1, filtered.size());
        assertEquals("College_HallTicket.pdf", filtered.get(0).getName());
    }

    @Test
    public void testDuplicateUriCandidateIsSkipped() {
        Uri uri = Uri.parse("content://media/external/file/101");
        DiscoveredCandidate candidate = new DiscoveredCandidate(
                uri,
                "College_HallTicket.pdf",
                "application/pdf",
                102400L,
                "/storage/emulated/0/Download/College_HallTicket.pdf",
                System.currentTimeMillis()
        );

        DocumentEntity doc = new DocumentEntity(
                "College_HallTicket.pdf",
                "College_HallTicket.pdf",
                "Hall Ticket",
                "application/pdf",
                "content://media/external/file/101",
                "/data/user/0/com.example/files/College/College_HallTicket.pdf",
                "College",
                "Hall Ticket"
        );
        doc.setId(1L);
        fakeDao.insertDocument(doc);

        TestStorageManager testStorageManager = new TestStorageManager();
        testStorageManager.stubHash = "different_hash_xyz";

        List<DiscoveredCandidate> filtered = candidateRepository.filterUnimportedCandidates(testStorageManager, Collections.singletonList(candidate));
        assertTrue(filtered.isEmpty());
    }

    @Test
    public void testDuplicateContentHashCandidateIsSkipped() {
        Uri uri = Uri.parse("content://media/external/file/102");
        DiscoveredCandidate candidate = new DiscoveredCandidate(
                uri,
                "Copy_Of_HallTicket.pdf",
                "application/pdf",
                102400L,
                "/storage/emulated/0/Download/Copy_Of_HallTicket.pdf",
                System.currentTimeMillis()
        );

        DocumentEntity doc = new DocumentEntity(
                "Original_HallTicket.pdf",
                "Original_HallTicket.pdf",
                "Hall Ticket",
                "application/pdf",
                "content://media/external/file/999",
                "/data/user/0/com.example/files/College/Original_HallTicket.pdf",
                "College",
                "Hall Ticket"
        );
        doc.setId(1L);
        doc.setContentHash("same_content_hash_123");
        fakeDao.insertDocument(doc);

        TestStorageManager testStorageManager = new TestStorageManager();
        testStorageManager.stubHash = "same_content_hash_123";

        List<DiscoveredCandidate> filtered = candidateRepository.filterUnimportedCandidates(testStorageManager, Collections.singletonList(candidate));
        assertTrue(filtered.isEmpty());
    }

    @Test
    public void testSameNameAndSizeCandidateIsSkipped() {
        Uri uri = Uri.parse("content://media/external/file/103");
        DiscoveredCandidate candidate = new DiscoveredCandidate(
                uri,
                "ImportantDoc.pdf",
                "application/pdf",
                51200L,
                "/storage/emulated/0/Download/ImportantDoc.pdf",
                System.currentTimeMillis()
        );

        DocumentEntity doc = new DocumentEntity(
                "ImportantDoc.pdf",
                "ImportantDoc.pdf",
                "ImportantDoc",
                "application/pdf",
                "content://media/external/file/888",
                "/data/user/0/com.example/files/Other/ImportantDoc.pdf",
                "Other Documents",
                "Other Document"
        );
        doc.setId(2L);
        doc.setFileSize(51200L);
        doc.setContentHash("");
        fakeDao.insertDocument(doc);

        TestStorageManager testStorageManager = new TestStorageManager();
        testStorageManager.stubHash = "random_hash";

        List<DiscoveredCandidate> filtered = candidateRepository.filterUnimportedCandidates(testStorageManager, Collections.singletonList(candidate));
        assertTrue(filtered.isEmpty());
    }
}
