package cl.coders.faketraveler.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One mock-location run. Child {@link RoutePointEntity} rows record the path (Module 1). */
@Entity(tableName = "mock_sessions")
public class MockSessionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long startTime;
    public long endTime;
    public double startLat;
    public double startLng;
    public double endLat;
    public double endLng;
    public int mockCount;
    public int mockFrequencyMs;
    public boolean completed;

    /** User-named label; null until the user sets one. */
    public String sessionLabel;
}
