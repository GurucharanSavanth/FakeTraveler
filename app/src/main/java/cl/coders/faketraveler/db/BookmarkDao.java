package cl.coders.faketraveler.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Room access for {@link BookmarkEntity}. Reads return {@link LiveData} so UI fragments
 * can observe the DAO directly without leaking a database reference into the adapter
 * (see {@code BookmarksBottomSheet}).
 */
@Dao
public interface BookmarkDao {

    @Insert
    long insert(BookmarkEntity bookmark);

    @Update
    void update(BookmarkEntity bookmark);

    @Delete
    void delete(BookmarkEntity bookmark);

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    LiveData<List<BookmarkEntity>> getAll();

    /** Synchronous variant for tests and background-thread callers. */
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    List<BookmarkEntity> getAllSync();

    @Query("DELETE FROM bookmarks")
    void deleteAll();
}
