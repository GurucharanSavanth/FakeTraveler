package cl.coders.faketraveler;

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.TimerTask;

/**
 * Periodic mock-location push. Extracted from {@link MockedLocationService} (FIX-015).
 * Mutates internal lat/lng each tick by the configured deltas; auto-cancels when the
 * configured {@code maxLocationTimes} is reached (or runs forever when {@code maxLocationTimes == 0}).
 */
final class MockedLocationTask extends TimerTask {

    @NonNull
    private final MockedLocationService service;
    private final float speed;
    private final double longitudeMockedDistance;
    private final double latitudeMockedDistance;
    private final int maxLocationTimes;
    private double longitude;
    private double latitude;
    private int currentTimes = 0;
    /** FIX-026 (Phase 3.4): Timer.cancel() does NOT interrupt a running task — track manually. */
    private volatile boolean cancelled = false;

    MockedLocationTask(@NonNull MockedLocationService service,
                       double longitude, double latitude,
                       double longitudeMockedDistance, double latitudeMockedDistance,
                       int maxTimes, float mockSpeed) {
        this.service = service;
        this.longitude = longitude;
        this.latitude = latitude;
        this.longitudeMockedDistance = longitudeMockedDistance;
        this.latitudeMockedDistance = latitudeMockedDistance;
        this.maxLocationTimes = maxTimes;
        this.speed = mockSpeed;
    }

    @Override
    public boolean cancel() {
        cancelled = true;
        return super.cancel();
    }

    @Override
    public void run() {
        if (cancelled) return;                                                          // FIX-026
        final Location value = new Location(LocationManager.GPS_PROVIDER);
        value.setLongitude(longitude);
        value.setLatitude(latitude);
        if (speed > 0) {
            value.setSpeed(speed);
            value.setAccuracy(0.1f);
            value.setTime(System.currentTimeMillis());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                value.setSpeedAccuracyMetersPerSecond(0.01f);
            }
        }

        // Re-check cancelled immediately before publishing — stopMockNow() on main thread
        // can flip the flag after the initial L51 gate but before we touch the service.
        // Without this gate, a stale tick bumps the *next* mock's progress counter.
        if (cancelled) return;
        service.publishLocation(value, latitude, longitude);
        ++currentTimes;
        if (maxLocationTimes != 0 && maxLocationTimes == currentTimes) {
            this.cancel();
            service.notifyMockCompleted();
            return;
        }
        latitude += latitudeMockedDistance;
        longitude += longitudeMockedDistance;
    }
}
