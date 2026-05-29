package cl.coders.faketraveler.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A reusable, user-authored route. Child {@link RouteWaypointEntity} rows hold the path (Module 2). */
@Entity(tableName = "saved_routes")
public class SavedRouteEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    public String description;
    public long createdAt;
    public int pointCount;
    public double totalDistanceMeters;
    public int estimatedDurationSeconds;
}
