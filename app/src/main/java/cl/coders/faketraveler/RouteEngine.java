package cl.coders.faketraveler;

import android.location.Location;
import android.location.LocationManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import cl.coders.faketraveler.db.RouteWaypointEntity;

/**
 * Module 2: converts a list of {@link RouteWaypointEntity} into a pre-computed sequence of
 * {@link Location} samples for {@link RoutePlayer}. Pure function, no Android service dependency, so
 * it is unit-testable in isolation.
 *
 * <p>Between consecutive waypoints it linearly interpolates lat/lng; the number of samples is the
 * segment travel time (distance ÷ speed) divided by {@code mockFrequencyMs}. Distance comes from
 * {@link Location#distanceBetween}. A waypoint's {@code dwellMs} appends stationary samples. When
 * {@code simulateAltitude} is on, altitude follows a gentle sinusoid so successive points are not
 * identical.
 */
public final class RouteEngine {

    private static final double BASE_ALTITUDE_M = 50.0;

    private RouteEngine() {}

    @NonNull
    public static List<Location> buildPath(@NonNull List<RouteWaypointEntity> wps,
                                           long mockFrequencyMs, boolean simulateAltitude) {
        final List<Location> out = new ArrayList<>();
        if (wps.size() < 2 || mockFrequencyMs <= 0) return out;

        for (int i = 0; i < wps.size() - 1; i++) {
            final RouteWaypointEntity a = wps.get(i);
            final RouteWaypointEntity b = wps.get(i + 1);

            final float[] res = new float[1];
            Location.distanceBetween(a.lat, a.lng, b.lat, b.lng, res);
            final double distMeters = res[0];
            final double speedMs = Math.max(0.1, a.speedKmh / 3.6);
            final double travelMs = (distMeters / speedMs) * 1000.0;
            final int steps = Math.max(1, (int) Math.ceil(travelMs / mockFrequencyMs));

            for (int s = 0; s < steps; s++) {
                final double t = (double) s / steps;
                final Location loc = new Location(LocationManager.GPS_PROVIDER);
                loc.setLatitude(a.lat + (b.lat - a.lat) * t);
                loc.setLongitude(a.lng + (b.lng - a.lng) * t);
                loc.setSpeed((float) speedMs);
                loc.setAccuracy(0.1f);
                if (simulateAltitude) loc.setAltitude(BASE_ALTITUDE_M + 10.0 * Math.sin(t * Math.PI + i));
                out.add(loc);
            }

            if (b.dwellMs > 0) {
                final int dwellSteps = (int) Math.max(1, b.dwellMs / mockFrequencyMs);
                for (int d = 0; d < dwellSteps; d++) {
                    final Location loc = new Location(LocationManager.GPS_PROVIDER);
                    loc.setLatitude(b.lat);
                    loc.setLongitude(b.lng);
                    loc.setSpeed(0f);
                    loc.setAccuracy(0.1f);
                    if (simulateAltitude) loc.setAltitude(BASE_ALTITUDE_M);
                    out.add(loc);
                }
            }
        }

        final RouteWaypointEntity last = wps.get(wps.size() - 1);
        final Location end = new Location(LocationManager.GPS_PROVIDER);
        end.setLatitude(last.lat);
        end.setLongitude(last.lng);
        end.setAccuracy(0.1f);
        if (simulateAltitude) end.setAltitude(BASE_ALTITUDE_M);
        out.add(end);
        return out;
    }
}
