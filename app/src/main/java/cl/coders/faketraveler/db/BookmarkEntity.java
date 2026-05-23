package cl.coders.faketraveler.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Persisted record of a user-saved location. */
@Entity(tableName = "bookmarks")
public class BookmarkEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    public double lat;
    public double lng;
    public int zoom;
    public long createdAt;
}
