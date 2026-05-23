package cl.coders.faketraveler.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room database singleton. v2 schema holds a single {@link BookmarkEntity} table.
 *
 * <p>The v1->v2 migration renames the legacy {@code favorites} table to {@code bookmarks}
 * via CREATE+INSERT+DROP so existing user data is preserved across the rename. No
 * destructive fallback is wired — adding one would mask migration regressions.
 *
 * <p>Note: until the next real compile, the hand-authored
 * {@code app/schemas/.../AppDatabase/2.json} carries {@code identityHash =
 * "PENDING_FIRST_BUILD"}. Room overwrites it with the real hash on first build; tests
 * read the entity class directly so they are not blocked by the placeholder.
 */
@Database(entities = {BookmarkEntity.class}, version = 2, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract BookmarkDao bookmarkDao();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Cross-version-safe rename: copy rows into the new table, then drop the old one.
            db.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`lat` REAL NOT NULL, " +
                    "`lng` REAL NOT NULL, " +
                    "`zoom` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)");
            db.execSQL("INSERT INTO `bookmarks` (`id`, `name`, `lat`, `lng`, `zoom`, `createdAt`) " +
                    "SELECT `id`, `name`, `lat`, `lng`, `zoom`, `createdAt` FROM `favorites`");
            db.execSQL("DROP TABLE `favorites`");
        }
    };

    @NonNull
    public static AppDatabase get(@NonNull Context ctx) {
        AppDatabase db = INSTANCE;
        if (db == null) synchronized (AppDatabase.class) {
            db = INSTANCE;
            if (db == null) INSTANCE = db = Room.databaseBuilder(
                    ctx.getApplicationContext(), AppDatabase.class, "faketraveler.db")
                    .addMigrations(MIGRATION_1_2)
                    .build();
        }
        return db;
    }
}
