package cl.coders.faketraveler.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Ordered waypoint of a {@link SavedRouteEntity} (Module 2). */
@Entity(tableName = "route_waypoints",
        foreignKeys = @ForeignKey(entity = SavedRouteEntity.class,
                parentColumns = "id", childColumns = "routeId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("routeId"))
public class RouteWaypointEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long routeId;
    public int sequence;
    public double lat;
    public double lng;
    /** Target speed travelling to the next waypoint. */
    public double speedKmh;
    /** Pause at this point before moving on. */
    public long dwellMs;
}
