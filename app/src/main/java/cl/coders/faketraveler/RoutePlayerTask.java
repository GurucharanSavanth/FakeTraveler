package cl.coders.faketraveler;

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.TimerTask;

/**
 * Module 2: walks a pre-computed {@link Location} path one sample per tick, pushing each through
 * {@link MockedLocationService#publishLocation}. Sibling of {@link MockedLocationTask} (drift math)
 * — same Timer + cancel discipline (FIX-026), different source. Loops when {@code loop} is set,
 * otherwise completes via {@link MockedLocationService#notifyMockCompleted} at the end.
 */
final class RoutePlayerTask extends TimerTask {

    @NonNull private final MockedLocationService service;
    @NonNull private final List<Location> path;
    private final boolean loop;
    private int index = 0;
    private volatile boolean cancelled = false;

    RoutePlayerTask(@NonNull MockedLocationService service, @NonNull List<Location> path, boolean loop) {
        this.service = service;
        this.path = path;
        this.loop = loop;
    }

    @Override
    public boolean cancel() {
        cancelled = true;
        return super.cancel();
    }

    @Override
    public void run() {
        if (cancelled) return;
        if (path.isEmpty()) {
            this.cancel();
            service.notifyMockCompleted();
            return;
        }
        if (index >= path.size()) {
            if (loop) {
                index = 0;
            } else {
                this.cancel();
                service.notifyMockCompleted();
                return;
            }
        }

        final Location base = path.get(index);
        final Location value = new Location(LocationManager.GPS_PROVIDER);
        value.setLatitude(base.getLatitude());
        value.setLongitude(base.getLongitude());
        if (base.hasAltitude()) value.setAltitude(base.getAltitude());
        value.setAccuracy(base.hasAccuracy() ? base.getAccuracy() : 0.1f);
        value.setTime(System.currentTimeMillis());
        if (base.hasSpeed()) {
            value.setSpeed(base.getSpeed());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                value.setSpeedAccuracyMetersPerSecond(0.01f);
            }
        }

        if (cancelled) return;
        service.publishLocation(value, base.getLatitude(), base.getLongitude());
        index++;
    }
}
