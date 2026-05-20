package cl.coders.faketraveler;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/** Wraps a single {@link LocationManager} test provider. FIX-003. */
public class MockedLocationProvider {

    @NonNull
    private static final String TAG = MockedLocationProvider.class.getSimpleName();

    @NonNull
    private final String providerName;
    @NonNull
    private final Context ctx;
    @NonNull
    private final LocationManager lm;

    /**
     * Construct and register a mock test provider. Throws {@link SecurityException} if this app is
     * not the selected mock-location app.
     *
     * @param name provider name (e.g. {@link LocationManager#GPS_PROVIDER})
     * @param ctx  application context
     */
    public MockedLocationProvider(@NonNull String name, @NonNull Context ctx) {
        this.providerName = name;
        this.ctx = ctx;
        this.lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        startup();
    }

    private void startup() {
        // Defensive: idempotent — remove any leftover provider before adding. FIX-003.
        try {
            lm.removeTestProvider(providerName);
        } catch (Throwable ignored) {
            // may not exist yet; safe to ignore
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                addTestProviderModern();
            } else {
                addTestProviderLegacy();
            }
            lm.setTestProviderEnabled(providerName, true);
        } catch (SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "addTestProvider failed for " + providerName, e);
            throw e instanceof SecurityException ? (SecurityException) e
                    : new SecurityException("Not allowed to mock location for " + providerName, e);
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private void addTestProviderModern() {
        final ProviderProperties props = new ProviderProperties.Builder()
                .setHasAltitudeSupport(true)
                .setHasSpeedSupport(true)
                .setHasBearingSupport(true)
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .build();
        lm.addTestProvider(providerName, props);
    }

    @SuppressLint("WrongConstant") // legacy LocationProvider POWER_/ACCURACY_ ints predate ProviderProperties; lint mis-types them
    @SuppressWarnings("deprecation")
    private void addTestProviderLegacy() {
        // API 21-30 path. powerUsage / accuracy enums per platform defaults.
        final int powerUsage = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ? 1 : 0;
        final int accuracy = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ? 2 : 5;
        lm.addTestProvider(
                providerName,
                /* requiresNetwork    */ false,
                /* requiresSatellite  */ false,
                /* requiresCell       */ false,
                /* hasMonetaryCost    */ false,
                /* supportsAltitude   */ false,
                /* supportsSpeed      */ true,
                /* supportsBearing    */ true,
                powerUsage,
                accuracy);
    }

    /**
     * Push a single mocked location to the underlying test provider.
     *
     * @param lat latitude  in decimal degrees
     * @param lon longitude in decimal degrees
     */
    public void pushLocation(double lat, double lon) {
        final Location m = new Location(providerName);
        m.setLatitude(lat);
        m.setLongitude(lon);
        m.setAltitude(3F);
        m.setTime(System.currentTimeMillis());
        m.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        m.setSpeed(0.01F);
        m.setBearing(1F);
        m.setAccuracy(3F);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            m.setBearingAccuracyDegrees(0.1F);
            m.setVerticalAccuracyMeters(0.1F);
            m.setSpeedAccuracyMetersPerSecond(0.01F);
        }
        try {
            lm.setTestProviderLocation(providerName, m);
        } catch (SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "setTestProviderLocation failed for " + providerName, e);
        }
    }

    /** Tear down the provider. Safe to call multiple times. */
    public void shutdown() {
        try {
            lm.removeTestProvider(providerName);
        } catch (Throwable ignored) {
            // already removed, or app no longer selected as mock provider
        }
    }
}
