package com.example.personaldocumentfinder.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {DocumentEntity.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract DocumentDao documentDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE documents ADD COLUMN displayName TEXT NOT NULL DEFAULT ''");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // Version 2 to 3 migration: ensure displayName is non-null and defaults to documentType if empty
            db.execSQL("UPDATE documents SET displayName = documentType WHERE displayName IS NULL OR displayName = ''");
        }
    };

    public static final Migration MIGRATION_1_3 = new Migration(1, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE documents ADD COLUMN displayName TEXT NOT NULL DEFAULT ''");
            db.execSQL("UPDATE documents SET displayName = documentType WHERE displayName IS NULL OR displayName = ''");
        }
    };

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "personal_document_finder_db"
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
