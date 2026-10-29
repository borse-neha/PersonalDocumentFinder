package com.example.personaldocumentfinder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

import com.example.personaldocumentfinder.data.AppDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class AppDatabaseMigrationTest {

    private static final String TEST_DB = "migration-test.db";

    private Context getContext() {
        return RuntimeEnvironment.getApplication();
    }

    @Test
    public void testMigration1To2_addsDisplayNameColumn() throws Exception {
        getContext().deleteDatabase(TEST_DB);

        SupportSQLiteOpenHelper.Configuration config = SupportSQLiteOpenHelper.Configuration.builder(getContext())
                .name(TEST_DB)
                .callback(new SupportSQLiteOpenHelper.Callback(1) {
                    @Override
                    public void onCreate(SupportSQLiteDatabase db) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS documents (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "originalName TEXT, " +
                                "storedFileName TEXT, " +
                                "mimeType TEXT, " +
                                "originalUri TEXT, " +
                                "internalPath TEXT, " +
                                "category TEXT, " +
                                "documentType TEXT, " +
                                "documentConfidence REAL NOT NULL, " +
                                "categoryConfidence REAL NOT NULL, " +
                                "ocrText TEXT, " +
                                "fileSize INTEGER NOT NULL, " +
                                "dateImported INTEGER NOT NULL, " +
                                "lastModified INTEGER NOT NULL, " +
                                "isFavorite INTEGER NOT NULL, " +
                                "isReviewed INTEGER NOT NULL, " +
                                "contentHash TEXT)");
                    }

                    @Override
                    public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {}
                })
                .build();

        SupportSQLiteDatabase db = new FrameworkSQLiteOpenHelperFactory().create(config).getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put("originalName", "pan_card.jpg");
        cv.put("storedFileName", "pan_card.jpg");
        cv.put("mimeType", "image/jpeg");
        cv.put("originalUri", "content://media/1");
        cv.put("internalPath", "/storage/pan_card.jpg");
        cv.put("category", "Identity");
        cv.put("documentType", "PAN Card");
        cv.put("documentConfidence", 0.95);
        cv.put("categoryConfidence", 0.95);
        cv.put("ocrText", "INCOME TAX DEPARTMENT");
        cv.put("fileSize", 50000);
        cv.put("dateImported", 1000);
        cv.put("lastModified", 1000);
        cv.put("isFavorite", 0);
        cv.put("isReviewed", 1);
        cv.put("contentHash", "hash123");
        db.insert("documents", 0, cv);

        // Run Migration 1 -> 2
        AppDatabase.MIGRATION_1_2.migrate(db);

        // Verify displayName column exists and has default ''
        Cursor cursor = db.query("SELECT id, displayName, documentType FROM documents WHERE id = 1");
        assertTrue(cursor.moveToFirst());
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("displayName")));
        assertEquals("PAN Card", cursor.getString(cursor.getColumnIndexOrThrow("documentType")));
        cursor.close();
        db.close();
    }

    @Test
    public void testMigration2To3_populatesDisplayNameFromDocumentType() throws Exception {
        getContext().deleteDatabase(TEST_DB);

        SupportSQLiteOpenHelper.Configuration config = SupportSQLiteOpenHelper.Configuration.builder(getContext())
                .name(TEST_DB)
                .callback(new SupportSQLiteOpenHelper.Callback(2) {
                    @Override
                    public void onCreate(SupportSQLiteDatabase db) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS documents (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "originalName TEXT, " +
                                "storedFileName TEXT, " +
                                "displayName TEXT NOT NULL DEFAULT '', " +
                                "mimeType TEXT, " +
                                "originalUri TEXT, " +
                                "internalPath TEXT, " +
                                "category TEXT, " +
                                "documentType TEXT, " +
                                "documentConfidence REAL NOT NULL, " +
                                "categoryConfidence REAL NOT NULL, " +
                                "ocrText TEXT, " +
                                "fileSize INTEGER NOT NULL, " +
                                "dateImported INTEGER NOT NULL, " +
                                "lastModified INTEGER NOT NULL, " +
                                "isFavorite INTEGER NOT NULL, " +
                                "isReviewed INTEGER NOT NULL, " +
                                "contentHash TEXT)");
                    }

                    @Override
                    public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {}
                })
                .build();

        SupportSQLiteDatabase db = new FrameworkSQLiteOpenHelperFactory().create(config).getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put("originalName", "aadhaar.pdf");
        cv.put("storedFileName", "aadhaar.pdf");
        cv.put("displayName", ""); // Empty displayName in v2
        cv.put("mimeType", "application/pdf");
        cv.put("originalUri", "content://media/2");
        cv.put("internalPath", "/storage/aadhaar.pdf");
        cv.put("category", "Identity");
        cv.put("documentType", "Aadhaar Card");
        cv.put("documentConfidence", 0.90);
        cv.put("categoryConfidence", 0.90);
        cv.put("ocrText", "GOVERNMENT OF INDIA");
        cv.put("fileSize", 80000);
        cv.put("dateImported", 1000);
        cv.put("lastModified", 1000);
        cv.put("isFavorite", 1);
        cv.put("isReviewed", 1);
        cv.put("contentHash", "hash456");
        db.insert("documents", 0, cv);

        // Run Migration 2 -> 3
        AppDatabase.MIGRATION_2_3.migrate(db);

        // Verify displayName was updated to documentType
        Cursor cursor = db.query("SELECT displayName FROM documents WHERE id = 1");
        assertTrue(cursor.moveToFirst());
        assertEquals("Aadhaar Card", cursor.getString(cursor.getColumnIndexOrThrow("displayName")));
        cursor.close();
        db.close();
    }

    @Test
    public void testMigration1To3_directUpgradePreservesData() throws Exception {
        getContext().deleteDatabase(TEST_DB);

        SupportSQLiteOpenHelper.Configuration config = SupportSQLiteOpenHelper.Configuration.builder(getContext())
                .name(TEST_DB)
                .callback(new SupportSQLiteOpenHelper.Callback(1) {
                    @Override
                    public void onCreate(SupportSQLiteDatabase db) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS documents (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "originalName TEXT, " +
                                "storedFileName TEXT, " +
                                "mimeType TEXT, " +
                                "originalUri TEXT, " +
                                "internalPath TEXT, " +
                                "category TEXT, " +
                                "documentType TEXT, " +
                                "documentConfidence REAL NOT NULL, " +
                                "categoryConfidence REAL NOT NULL, " +
                                "ocrText TEXT, " +
                                "fileSize INTEGER NOT NULL, " +
                                "dateImported INTEGER NOT NULL, " +
                                "lastModified INTEGER NOT NULL, " +
                                "isFavorite INTEGER NOT NULL, " +
                                "isReviewed INTEGER NOT NULL, " +
                                "contentHash TEXT)");
                    }

                    @Override
                    public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {}
                })
                .build();

        SupportSQLiteDatabase db = new FrameworkSQLiteOpenHelperFactory().create(config).getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put("originalName", "college_fee.pdf");
        cv.put("storedFileName", "college_fee.pdf");
        cv.put("mimeType", "application/pdf");
        cv.put("originalUri", "content://media/3");
        cv.put("internalPath", "/storage/college_fee.pdf");
        cv.put("category", "College");
        cv.put("documentType", "Fee Receipt");
        cv.put("documentConfidence", 0.88);
        cv.put("categoryConfidence", 0.88);
        cv.put("ocrText", "TUITION FEES PAID");
        cv.put("fileSize", 64000);
        cv.put("dateImported", 1000);
        cv.put("lastModified", 1000);
        cv.put("isFavorite", 0);
        cv.put("isReviewed", 1);
        cv.put("contentHash", "hash789");
        db.insert("documents", 0, cv);

        // Run Migration 1 -> 3
        AppDatabase.MIGRATION_1_3.migrate(db);

        // Verify column added and populated without data loss
        Cursor cursor = db.query("SELECT displayName, category, documentType, contentHash FROM documents WHERE id = 1");
        assertTrue(cursor.moveToFirst());
        assertEquals("Fee Receipt", cursor.getString(cursor.getColumnIndexOrThrow("displayName")));
        assertEquals("College", cursor.getString(cursor.getColumnIndexOrThrow("category")));
        assertEquals("hash789", cursor.getString(cursor.getColumnIndexOrThrow("contentHash")));
        cursor.close();
        db.close();
    }
}
