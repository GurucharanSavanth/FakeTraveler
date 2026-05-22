package cl.coders.faketraveler.route;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cl.coders.faketraveler.GpxImporter;
import cl.coders.faketraveler.joystick.JoystickEngine;

/**
 * Builds a {@link GpxImporter.Route} from a sequence of tapped waypoints by interpolating
 * intermediate points at {@link #interpolationStepKm} spacing. Without interpolation a
 * route playback would teleport between taps; with it the route behaves as a continuous
 * track.
 */
public final class MultiSpotRouteBuilder {

    @NonNull private final List<GpxImporter.TrackPoint> taps = new ArrayList<>();
    private double interpolationStepKm = 0.05;        // 50 m

    public void setInterpolationStepKm(double step) {
        if (step > 0) interpolationStepKm = step;
    }

    public void addTap(double lat, double lng) {
        taps.add(new GpxImporter.TrackPoint(lat, lng));
    }

    public int size() { return taps.size(); }

    public void clear() { taps.clear(); }

    @NonNull
    public GpxImporter.Route build() {
        final List<GpxImporter.TrackPoint> out = new ArrayList<>();
        for (int i = 0; i < taps.size(); i++) {
            GpxImporter.TrackPoint cur = taps.get(i);
            if (i == 0) { out.add(cur); continue; }
            GpxImporter.TrackPoint prev = taps.get(i - 1);
            final double distKm = JoystickEngine.haversineKm(
                    prev.lat(), prev.lon(), cur.lat(), cur.lon());
            final int steps = Math.max(1, (int) Math.ceil(distKm / interpolationStepKm));
            // Linear interpolation in degree space — close enough at city scale, breaks
            // down near the poles. Acceptable for the intended use case (tap-to-walk).
            for (int s = 1; s <= steps; s++) {
                final double t = (double) s / steps;
                out.add(new GpxImporter.TrackPoint(
                        prev.lat() + (cur.lat() - prev.lat()) * t,
                        prev.lon() + (cur.lon() - prev.lon()) * t));
            }
        }
        return new GpxImporter.Route(Collections.unmodifiableList(out), 1);
    }
}
