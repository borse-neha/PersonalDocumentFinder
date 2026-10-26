package com.example.personaldocumentfinder;

import android.content.Context;
import android.net.Uri;
import com.example.personaldocumentfinder.data.DocumentEntity;
import com.example.personaldocumentfinder.data.DocumentRepository;
import com.example.personaldocumentfinder.domain.CandidateRepository;
import com.example.personaldocumentfinder.domain.DiscoveredCandidate;
import com.example.personaldocumentfinder.domain.FileOpener;
import com.example.personaldocumentfinder.domain.StorageManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class DocumentStorageTest {

    private Context context;
    private FakeDocumentDao fakeDao;
    private DocumentRepository repository;
    private CandidateRepository candidateRepository;
    private StorageManager storageManager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        fakeDao = new FakeDocumentDao();
        repository = new DocumentRepository(fakeDao);
        candidateRepository = new CandidateRepository(repository);
        storageManager = new StorageManager(context);
    }

    private File createDummySourceFile(String fileName, String content) throws Exception {
        File originalDir = new File(context.getCacheDir(), "original_user_documents");
        if (!originalDir.exists()) originalDir.mkdirs();
        File originalFile = new File(originalDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(originalFile)) {
            fos.write(content.getBytes());
        }
        return originalFile;
    }

    // 30. Classified document is physically copied to correct phone storage category.
    @Test
    public void test30_ClassifiedDocumentPhysicallyCopiedToCorrectPhoneStorageCategory() throws Exception {
        File original = createDummySourceFile("Aadhaar_Original.jpg", "Aadhaar Card Data 1234 5678 9012");
        Uri uri = Uri.fromFile(original);

        StorageManager.CopyResult copyResult = storageManager.copyFileToOrganizedStorage(uri, "Identity", "Aadhaar_Card.jpg");
        assertNotNull(copyResult);

        File organizedFile = new File(copyResult.getInternalPath());
        assertTrue("Organized file must physically exist", organizedFile.exists());
        assertEquals("Aadhaar_Card.jpg", organizedFile.getName());
        assertEquals("Identity", organizedFile.getParentFile() != null ? organizedFile.getParentFile().getName() : null);
    }

    // 31. Database points to the organized file.
    @Test
    public void test31_DatabasePointsToTheOrganizedFile() throws Exception {
        File original = createDummySourceFile("Vehicle_RC_Original.pdf", "Motor Vehicle Registration MH-12-AB-1234");
        Uri uri = Uri.fromFile(original);

        StorageManager.CopyResult copyResult = storageManager.copyFileToOrganizedStorage(uri, "Vehicle", "Vehicle_RC.pdf");
        assertNotNull(copyResult);

        DocumentEntity entity = new DocumentEntity(
                "Vehicle_RC_Original.pdf",
                copyResult.getStoredFileName(),
                "Vehicle Registration Certificate",
                "application/pdf",
                uri.toString(),
                copyResult.getInternalPath(),
                "Vehicle",
                "Vehicle RC"
        );
        entity.setId(31L);
        entity.setContentHash(copyResult.getContentHash());
        fakeDao.insertDocument(entity);

        DocumentEntity retrieved = repository.getDocumentById(31L);
        assertNotNull(retrieved);
        assertEquals(copyResult.getInternalPath(), retrieved.getInternalPath());
        File fileOnDisk = new File(retrieved.getInternalPath());
        assertTrue("File pointed to by DB must exist on disk", fileOnDisk.exists());
    }

    // 32. File can be opened after simulated app restart.
    @Test
    public void test32_FileCanBeOpenedAfterAppRestart() throws Exception {
        File original = createDummySourceFile("HallTicket.pdf", "University Examination Hall Ticket");
        Uri uri = Uri.fromFile(original);
        StorageManager.CopyResult copyResult = storageManager.copyFileToOrganizedStorage(uri, "College", "Hall_Ticket.pdf");
        assertNotNull(copyResult);

        DocumentEntity entity = new DocumentEntity(
                "HallTicket.pdf",
                copyResult.getStoredFileName(),
                "Hall Ticket",
                "application/pdf",
                uri.toString(),
                copyResult.getInternalPath(),
                "College",
                "Hall Ticket"
        );
        entity.setId(32L);
        fakeDao.insertDocument(entity);

        // Simulate app restart: re-initialize repository & storage manager
        DocumentRepository newRepository = new DocumentRepository(fakeDao);
        DocumentEntity restoredDoc = newRepository.getDocumentById(32L);
        assertNotNull(restoredDoc);

        File targetFile = new File(restoredDoc.getInternalPath());
        assertTrue("File must still exist after restart", targetFile.exists());
        assertTrue("File must be readable", targetFile.canRead());
        assertEquals("File size must match original content", original.length(), targetFile.length());

        // FileOpener checks file existence first; ensure it does not fail with "file no longer exists"
        FileOpener.OpenResult nonExistentResult = FileOpener.openDocument(context, "/invalid/path/missing.pdf", "application/pdf");
        assertTrue(nonExistentResult instanceof FileOpener.OpenResult.Error);
        assertEquals("Document file no longer exists in app storage.", ((FileOpener.OpenResult.Error) nonExistentResult).getMessage());

        FileOpener.OpenResult existingResult = FileOpener.openDocument(context, restoredDoc.getInternalPath(), restoredDoc.getMimeType());
        // Ensure existingResult does NOT report file missing
        if (existingResult instanceof FileOpener.OpenResult.Error) {
            assertFalse(
                    "Must not report file does not exist",
                    ((FileOpener.OpenResult.Error) existingResult).getMessage().contains("no longer exists")
            );
        }
    }

    // 33. Original file is preserved.
    @Test
    public void test33_OriginalFileIsPreserved() throws Exception {
        File original = createDummySourceFile("Important_Doc.pdf", "Original Unmodified Content");
        long originalLength = original.length();
        Uri uri = Uri.fromFile(original);

        StorageManager.CopyResult copyResult = storageManager.copyFileToOrganizedStorage(uri, "Personal", "Important_Doc.pdf");
        assertNotNull(copyResult);

        assertTrue("Original file must remain intact", original.exists());
        assertEquals("Original file size must remain unmodified", originalLength, original.length());
    }

    // 34. Duplicate scan does not create another copy.
    @Test
    public void test34_DuplicateScanDoesNotCreateAnotherCopy() throws Exception {
        File original = createDummySourceFile("Scan_Duplicate.pdf", "Duplicate Content Test");
        Uri uri = Uri.fromFile(original);
        StorageManager.CopyResult copyResult = storageManager.copyFileToOrganizedStorage(uri, "Other Documents", "Scan_Duplicate.pdf");
        assertNotNull(copyResult);

        DocumentEntity entity = new DocumentEntity(
                "Scan_Duplicate.pdf",
                copyResult.getStoredFileName(),
                "Other Document",
                "application/pdf",
                uri.toString(),
                copyResult.getInternalPath(),
                "Other Documents",
                "Other Document"
        );
        entity.setId(34L);
        entity.setContentHash(copyResult.getContentHash());
        fakeDao.insertDocument(entity);

        DiscoveredCandidate candidate = new DiscoveredCandidate(
                uri,
                "Scan_Duplicate.pdf",
                "application/pdf",
                original.length(),
                original.getAbsolutePath(),
                System.currentTimeMillis()
        );

        List<DiscoveredCandidate> unimported = candidateRepository.filterUnimportedCandidates(storageManager, Collections.singletonList(candidate));
        assertTrue("Duplicate candidate must be filtered out to prevent duplicate copies", unimported.isEmpty());
    }

    // 35. Zero-byte files are not copied or stored.
    @Test
    public void test35_ZeroByteFileIsNotCopiedOrStored() throws Exception {
        File emptyOriginal = createDummySourceFile("Empty_Doc.pdf", "");
        Uri uri = Uri.fromFile(emptyOriginal);

        StorageManager.CopyResult copyResult = storageManager.copyFileToOrganizedStorage(uri, "Other Documents", "Empty_Doc.pdf");
        assertTrue("Zero-byte file must not be copied", copyResult == null);

        File categoryDir = storageManager.getCategoryDir("Other Documents");
        File possibleFile = new File(categoryDir, "Empty_Doc.pdf");
        assertFalse("Empty file must not exist in storage", possibleFile.exists());
    }

    // 36. deleteAppPrivateFile rejects deleting external user files outside app boundary.
    @Test
    public void test36_DeleteAppPrivateFileRejectsDeletingExternalUserFiles() throws Exception {
        File externalUserFile = createDummySourceFile("External_Photo.jpg", "Precious User Photo Content");
        assertTrue("Original external user file must exist", externalUserFile.exists());

        // Attempting to delete external user file path directly must fail and not delete the file
        boolean deletionResult = storageManager.deleteAppPrivateFile(externalUserFile.getAbsolutePath());
        assertFalse("Must refuse to delete files outside app boundary", deletionResult);
        assertTrue("External user file must remain intact", externalUserFile.exists());
    }

    // 37. Moving file to same category returns existing path without creating copies.
    @Test
    public void test37_MoveFileToSameCategoryReturnsExistingPathWithoutDuplication() throws Exception {
        File original = createDummySourceFile("Doc_To_Move.pdf", "Content to Move");
        Uri uri = Uri.fromFile(original);
        StorageManager.CopyResult copyResult = storageManager.copyFileToOrganizedStorage(uri, "Finance", "Doc_To_Move.pdf");
        assertNotNull(copyResult);

        String initialPath = copyResult.getInternalPath();
        String sameCategoryPath = storageManager.moveFileToCategory(initialPath, "Finance");
        assertNotNull(sameCategoryPath);
        assertEquals("Path must remain identical when moving to the same category", initialPath, sameCategoryPath);
    }
}
