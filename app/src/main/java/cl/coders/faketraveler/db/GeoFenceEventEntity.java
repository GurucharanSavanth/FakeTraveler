package cl.coders.faketraveler.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A boundary transition logged while monitoring a {@link GeoFenceEntity} (Module 3). */
@Entity(tableName = "geofence_events")
public class GeoFenceEventEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long geofenceId;
    /** 0 = entry, 1 = exit, 2 = dwell. */
    public int eventType;
    public long timestamp;
    public double triggeredLat;
    public double triggeredLng;
    /** Links to {@link MockSessionEntity#id}; 0 when no session was active. */
    public long sessionId;
}
