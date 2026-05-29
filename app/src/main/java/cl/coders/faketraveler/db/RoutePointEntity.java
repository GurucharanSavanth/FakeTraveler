package cl.coders.faketraveler.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A single recorded point inside a {@link MockSessionEntity} run (Module 1). */
@Entity(tableName = "route_points",
        foreignKeys = @ForeignKey(entity = MockSessionEntity.class,
                parentColumns = "id", childColumns = "sessionId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("sessionId"))
public class RoutePointEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long sessionId;
    public int sequence;
    public double lat;
    public double lng;
    /** Metres above sea level. */
    public double altitude;
    /** Horizontal accuracy in metres. */
    public float accuracy;
    /** Offset from the owning session's startTime. */
    public long timestampOffsetMs;
}
