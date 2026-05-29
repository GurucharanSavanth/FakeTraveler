package cl.coders.faketraveler;

import android.location.Location;

import androidx.annotation.Nullable;

/**
 * Unified event emitted by {@link MockedLocationService} on its {@code mockEvents} LiveData so
 * feature modules (session recorder, geofence monitor, …) observe one stream instead of each
 * binding the raw location LiveData. {@code location} is null for START/STOP/ERROR.
 * {@code sessionId} is the service-assigned run id (millis at start), shared by every TICK of a run.
 */
public class MockEvent {

    public enum Type { START, TICK, STOP, ERROR, PAUSE, RESUME }

    public final Type type;
    @Nullable public final Location location;
    public final long timestamp;
    public final long sessionId;

    public MockEvent(Type type, @Nullable Location location, long timestamp, long sessionId) {
        this.type = type;
        this.location = location;
        this.timestamp = timestamp;
        this.sessionId = sessionId;
    }
}
