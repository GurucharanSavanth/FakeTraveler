package cl.coders.faketraveler.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A circular or polygonal boundary to monitor against the mock location (Module 3). */
@Entity(tableName = "geofences")
public class GeoFenceEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    public double centerLat;
    public double centerLng;
    /** Radius for circular fences. */
    public float radiusMeters;
    /** JSON array of [lat,lng] pairs for polygonal fences; null when circular. */
    public String polygonJson;
    /** 0 = circular, 1 = polygonal. */
    public int type;
    public boolean monitorEntry;
    public boolean monitorExit;
    public boolean monitorDwell;
    public long dwellMs;
    public boolean active;
}
