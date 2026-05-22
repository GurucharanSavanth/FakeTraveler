package cl.coders.faketraveler;

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.TimerTask;

import cl.coders.faketraveler.util.Inputs;

/**
 * Replays an imported GPX route, one point per tick. Loops at the end-of-route by default.
 * FIX-012.
 */
final class RoutePlaybackTask extends TimerTask {

    @NonNull
    private final MockedLocationService service;
    @NonNull
    private final List<GpxImporter.TrackPoint> points;
    private int index = 0;
    /** FIX-026 (Phase 3.4): track cancellation to short-circuit run() after Stop. */
    private volatile boolean cancelled = false;

    RoutePlaybackTask(@NonNull MockedLocationService service,
                      @NonNull List<GpxImporter.TrackPoint> points) {
        this.service = service;
        this.points = points;
    }

    @Override
    public boolean cancel() {
        cancelled = true;
        return super.cancel();
    }

    @Override
    public void run() {
        if (cancelled) return;                                                          // FIX-026
        if (points.isEmpty()) {
            this.cancel();
            service.notifyMockCompleted();
            return;
        }
        final GpxImporter.TrackPoint p = points.get(index);
        index++;
        if (index >= points.size()) index = 0; // loop
        if (!Inputs.validLat(p.lat()) || !Inputs.validLng(p.lon())) {
            return; // skip corrupt point; next tick advances index
        }
        final Location v = new Location(LocationManager.GPS_PROVIDER);
        v.setLatitude(p.lat());
        v.setLongitude(p.lon());
        v.setAccuracy(0.1f);
        v.setTime(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.setSpeedAccuracyMetersPerSecond(0.01f);
        }
        // Re-check cancel before publish so a stopMockNow() during this tick can't bump the
        // next mock's progress counter via service.publishLocation.
        if (cancelled) return;
        service.publishLocation(v, p.lat(), p.lon());
    }
}
