package cl.coders.faketraveler.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Room database singleton. v1 schema holds a single {@link FavoriteEntity} table.
 *
 * <p>{@code fallbackToDestructiveMigration()} is acceptable on v1 only because no shipped
 * users have data yet. It must be removed before v3.0.1 and replaced with explicit
 * {@code Migration} objects for every subsequent schema bump (V34).
 */
@Database(entities = {FavoriteEntity.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract FavoriteDao favoriteDao();

    @NonNull
    @SuppressWarnings("deprecation")  // V34 — v1 schema has no users to migrate
    public static AppDatabase get(@NonNull Context ctx) {
        AppDatabase db = INSTANCE;
        if (db == null) synchronized (AppDatabase.class) {
            db = INSTANCE;
            if (db == null) INSTANCE = db = Room.databaseBuilder(
                    ctx.getApplicationContext(), AppDatabase.class, "faketraveler.db")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return db;
    }
}
