package cl.coders.faketraveler;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Encapsulates {@link MockedLocationService} lifecycle: start (foreground), bind for IPC,
 * observe LiveData, unbind on Activity destruction, request stop via service intent.
 * FIX-015.
 */
public final class ServiceConnector implements ServiceConnection {

    @NonNull
    private static final String TAG = ServiceConnector.class.getSimpleName();

    /** Listener wired by MainActivity. */
    public interface Listener {
        void onMockedStateChange(@NonNull MockState s);
        void onMockedLocationChange(@NonNull Location loc);
        /** Called once the service binder is connected, after the core observers attach (P6–P8). */
        default void onBinderConnected(@NonNull MockedLocationService.MockedBinder binder) {}
    }

    /** Bundle of args to forward to {@link MockedLocationService#ACTION_START}. */
    public static final class MockArgs {
        public final double latitude;
        public final double longitude;
        public final double dLat;
        public final double dLng;
        public final long frequencyMillis;
        public final int count;
        public final float speed;

        public MockArgs(double latitude, double longitude,
                        double dLat, double dLng,
                        long frequencyMillis, int count, float speed) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.dLat = dLat;
            this.dLng = dLng;
            this.frequencyMillis = frequencyMillis;
            this.count = count;
            this.speed = speed;
        }
    }

    @NonNull
    private final AppCompatActivity activity;
    @NonNull
    private final Listener listener;
    @Nullable
    private MockedLocationService.MockedBinder binder;
    private boolean bound = false;

    public ServiceConnector(@NonNull AppCompatActivity a, @NonNull Listener l) {
        this.activity = a;
        this.listener = l;
    }

    @Nullable
    public MockedLocationService.MockedBinder binder() {
        return binder;
    }

    public boolean isBound() {
        return bound;
    }

    /** Start the foreground service with mock args and bind for IPC. */
    public void startAndBindForApply(@NonNull MockArgs args) {
        final Intent start = new Intent(activity, MockedLocationService.class)
                .setAction(MockedLocationService.ACTION_START)
                .putExtra(MockedLocationService.EXTRA_LATITUDE, args.latitude)
                .putExtra(MockedLocationService.EXTRA_LONGITUDE, args.longitude)
                .putExtra(MockedLocationService.EXTRA_D_LAT, args.dLat)
                .putExtra(MockedLocationService.EXTRA_D_LNG, args.dLng)
                .putExtra(MockedLocationService.EXTRA_FREQUENCY, args.frequencyMillis)
                .putExtra(MockedLocationService.EXTRA_COUNT, args.count)
                .putExtra(MockedLocationService.EXTRA_SPEED, args.speed);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(activity, start);
            } else {
                activity.startService(start);
            }
        } catch (Throwable t) {
            Log.e(TAG, "startForegroundService failed", t);
        }
        bind();
    }

    public void bind() {
        if (bound) return;
        final Intent bindIntent = new Intent(activity, MockedLocationService.class);
        try {
            bound = activity.bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            Log.e(TAG, "bindService failed", t);
            bound = false;
        }
    }

    /** FIX-028 (Phase 3.5): After process death, plain bind() won't trigger onStartCommand
     *  so resumeFromPrefsIfActive never runs. Issue a foreground RESUME start first, then bind. */
    public void resumeAndBind() {
        final Intent resume = new Intent(activity, MockedLocationService.class)
                .setAction(MockedLocationService.ACTION_RESUME);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(activity, resume);
            } else {
                activity.startService(resume);
            }
        } catch (Throwable t) {
            Log.e(TAG, "resume startForegroundService failed", t);
        }
        bind();
    }

    public void unbind() {
        if (binder != null) {
            try {
                binder.mockState.removeObservers(activity);
                binder.mockedLocation.removeObservers(activity);
            } catch (Throwable ignored) {
            }
        }
        if (bound) {
            try {
                activity.unbindService(this);
            } catch (IllegalArgumentException ignored) {
            }
            bound = false;
        }
        binder = null;
    }

    /** Send STOP intent (foreground service already running). */
    public void requestStop() {
        if (binder != null) {
            try { binder.requestStop(); return; } catch (Throwable ignored) {}
        }
        final Intent stop = new Intent(activity, MockedLocationService.class)
                .setAction(MockedLocationService.ACTION_STOP);
        try {
            activity.startService(stop);
        } catch (Throwable t) {
            Log.e(TAG, "requestStop failed", t);
        }
    }

    @Override
    public void onServiceConnected(@NonNull ComponentName name, @NonNull IBinder service) {
        binder = (MockedLocationService.MockedBinder) service;
        try {
            binder.mockState.observe(activity, listener::onMockedStateChange);
            binder.mockedLocation.observe(activity, listener::onMockedLocationChange);
            listener.onBinderConnected(binder);
        } catch (Throwable t) {
            Log.e(TAG, "Observer attach failed", t);
        }
    }

    @Override
    public void onServiceDisconnected(@NonNull ComponentName name) {
        binder = null;
        bound = false;
    }
}
