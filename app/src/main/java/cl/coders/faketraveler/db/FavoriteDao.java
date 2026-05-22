package cl.coders.faketraveler.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Room access for {@link FavoriteEntity}. Reads return {@link LiveData} so UI fragments
 * can observe the DAO directly without leaking a database reference into the adapter
 * (see {@code FavoritesBottomSheet}).
 */
@Dao
public interface FavoriteDao {

    @Insert
    long insert(FavoriteEntity favorite);

    @Update
    void update(FavoriteEntity favorite);

    @Delete
    void delete(FavoriteEntity favorite);

    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    LiveData<List<FavoriteEntity>> getAll();

    /** Synchronous variant for tests and background-thread callers. */
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    List<FavoriteEntity> getAllSync();

    @Query("DELETE FROM favorites")
    void deleteAll();
}
