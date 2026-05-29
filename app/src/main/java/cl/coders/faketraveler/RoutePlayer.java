package cl.coders.faketraveler;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import cl.coders.faketraveler.db.RouteWaypointEntity;

/**
 * Module 2: thin orchestrator that turns saved waypoints into a path via {@link RouteEngine} and
 * hands it to {@link MockedLocationService} through the bound {@link MockedLocationService.MockedBinder}.
 * The service runs the path on its existing Timer (see {@code RoutePlayerTask}); pause/resume/loop
 * are owned by the service task, not here.
 */
public final class RoutePlayer {

    private RoutePlayer() {}

    /**
     * @return true if a non-empty path was built and dispatched; false when the binder is missing or
     * the route has fewer than two waypoints.
     */
    public static boolean play(@Nullable MockedLocationService.MockedBinder binder,
                               @NonNull List<RouteWaypointEntity> waypoints,
                               long mockFrequencyMs, boolean loop, boolean simulateAltitude) {
        if (binder == null || waypoints.size() < 2) return false;
        final List<Location> path = RouteEngine.buildPath(waypoints, mockFrequencyMs, simulateAltitude);
        if (path.isEmpty()) return false;
        binder.startRoute(path, mockFrequencyMs, loop);
        return true;
    }
}
