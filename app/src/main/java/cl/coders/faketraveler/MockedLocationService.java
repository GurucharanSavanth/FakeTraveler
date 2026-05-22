package cl.coders.faketraveler;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import cl.coders.faketraveler.util.Inputs;

/**
 * Foreground service that owns mock location providers and the periodic push timer.
 * FIX-001, FIX-004, FIX-006, FIX-010.
 */
public class MockedLocationService extends Service {

    @NonNull
    private static final String TAG = MockedLocationService.class.getSimpleName();

    public static final String ACTION_START      = "cl.coders.faketraveler.action.START_MOCK";
    public static final String ACTION_STOP       = "cl.coders.faketraveler.action.STOP_MOCK";
    public static final String ACTION_RESUME     = "cl.coders.faketraveler.action.RESUME_MOCK";
    public static final String ACTION_PLAY_ROUTE = "cl.coders.faketraveler.action.PLAY_ROUTE";
    public static final String EXTRA_LATITUDE   = "cl.coders.faketraveler.extra.LAT";
    public static final String EXTRA_LONGITUDE  = "cl.coders.faketraveler.extra.LNG";
    public static final String EXTRA_D_LAT      = "cl.coders.faketraveler.extra.D_LAT";
    public static final String EXTRA_D_LNG      = "cl.coders.faketraveler.extra.D_LNG";
    public static final String EXTRA_FREQUENCY  = "cl.coders.faketraveler.extra.FREQ_MILLIS";
    public static final String EXTRA_COUNT      = "cl.coders.faketraveler.extra.COUNT";
    public static final String EXTRA_SPEED      = "cl.coders.faketraveler.extra.SPEED";
    public static final String EXTRA_ROUTE_JSON = "cl.coders.faketraveler.extra.ROUTE_JSON";

    @NonNull protected final MutableLiveData<MockState> mockState = new MutableLiveData<>(MockState.NOT_MOCKED);
    @NonNull protected final MutableLiveData<Location> mockedLocation = new MutableLiveData<>();
    /** FIX-027 (Phase 3.6): CopyOnWriteArrayList — Timer thread iterates via publishLocation()
     *  while main thread mutates via stopMockNow()/attachAllProviders(). */
    @NonNull private final List<MockedLocationProvider> providers = new CopyOnWriteArrayList<>();
    @NonNull private final Timer timer = new Timer("FT-MockTimer", true);
    @NonNull private final Set<TimerTask> tasks = Collections.synchronizedSet(new HashSet<>());

    @Nullable private StopReceiver stopReceiver;
    private boolean foregroundStarted = false;

    /** Progress accounting for the ongoing notification.
     *  {@code totalTicks == Integer.MAX_VALUE} renders an indeterminate spinner — this is
     *  the sentinel for an infinite mock (V24 / V40). AtomicInteger so Timer thread can
     *  increment {@code doneTicks} while UI thread reads it for the notification refresh
     *  (V47): the prior {@code volatile + ++} pattern was a read-modify-write race. */
    @NonNull private final AtomicInteger totalTicks = new AtomicInteger(Integer.MAX_VALUE);
    @NonNull private final AtomicInteger doneTicks = new AtomicInteger(0);

    @Override
    public void onCreate() {
        super.onCreate();
        promoteToForeground(null);                                                       // FIX-001
        registerStopReceiver();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null) {                                                            // FIX-010
            resumeFromPrefsIfActive();
            return START_STICKY;
        }
        final String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopMockNow();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) startFromIntent(intent);
        else if (ACTION_RESUME.equals(action)) resumeFromPrefsIfActive();
        else if (ACTION_PLAY_ROUTE.equals(action)) startRouteFromIntent(intent);
        return START_STICKY;
    }

    private void startRouteFromIntent(@NonNull Intent in) {
        final String json = in.getStringExtra(EXTRA_ROUTE_JSON);
        final long freqMillis = in.getLongExtra(EXTRA_FREQUENCY, 10_000L);
        final GpxImporter.Route route = json == null ? null : GpxImporter.fromJson(json);
        if (route == null || route.points().isEmpty()) {
            Log.w(TAG, "ACTION_PLAY_ROUTE missing or empty route");
            return;
        }
        try {
            stopMockNow();
            attachAllProviders();
            final TimerTask t = new RoutePlaybackTask(this, route.points());
            timer.schedule(t, 0L, freqMillis);
            tasks.add(t);
            mockState.postValue(MockState.MOCKED);
        } catch (SecurityException e) {
            Log.e(TAG, "Route playback failed to register providers", e);
            mockState.postValue(MockState.MOCK_ERROR);
        }
    }

    private void attachAllProviders() {
        providers.add(new MockedLocationProvider(LocationManager.GPS_PROVIDER, this));
        providers.add(new MockedLocationProvider(LocationManager.NETWORK_PROVIDER, this));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            providers.add(new MockedLocationProvider(LocationManager.FUSED_PROVIDER, this));
        }
    }

    @Override @NonNull
    public IBinder onBind(Intent intent) { return new MockedBinder(this); }

    @Override
    public boolean onUnbind(Intent intent) { return false; /* stay foreground */ }

    @Override
    public void onDestroy() {
        unregisterStopReceiver();
        stopMockNow();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForegroundLegacy();
        } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void stopForegroundLegacy() { stopForeground(true); }

    private void promoteToForeground(@Nullable Location currentLoc) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NotificationFactory.NOTIFICATION_ID,
                        NotificationFactory.buildOngoing(this, currentLoc),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NotificationFactory.NOTIFICATION_ID,
                        NotificationFactory.buildOngoing(this, currentLoc));
            }
            foregroundStarted = true;
        } catch (Throwable t) {
            Log.e(TAG, "startForeground failed", t);
        }
    }

    private void refreshNotification(@Nullable Location loc) {
        if (!foregroundStarted) return;
        try {
            final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NotificationFactory.NOTIFICATION_ID,
                        NotificationFactory.buildProgress(this, loc, totalTicks.get(), doneTicks.get()));
            }
        } catch (Throwable t) {
            Log.w(TAG, "Notification refresh failed", t);
        }
    }

    private void startFromIntent(@NonNull Intent in) {
        final double rawLat = in.getDoubleExtra(EXTRA_LATITUDE, Double.NaN);
        final double rawLng = in.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN);
        if (!Inputs.validLat(rawLat) || !Inputs.validLng(rawLng)) {
            Log.w(TAG, "ACTION_START rejected: lat/lng missing or out of range");
            return;
        }
        final double lat = Inputs.clampLat(rawLat);
        final double lng = Inputs.clampLng(rawLng);
        final double dLat = Inputs.sanitizeDelta(in.getDoubleExtra(EXTRA_D_LAT, 0d));
        final double dLng = Inputs.sanitizeDelta(in.getDoubleExtra(EXTRA_D_LNG, 0d));
        final long freqMillis = Inputs.clampFreqMs(in.getLongExtra(EXTRA_FREQUENCY, 10_000L));
        final int count = Inputs.clampCount(in.getIntExtra(EXTRA_COUNT, 0));
        final float speed = Inputs.sanitizeSpeed(in.getFloatExtra(EXTRA_SPEED, 0f));
        startMockedService(lng, lat, dLng, dLat, freqMillis, count, speed);
    }

    private void resumeFromPrefsIfActive() {
        try {
            final SharedPreferences p = getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
            final long endTime = p.getLong("endTime", 0L);
            if (endTime <= System.currentTimeMillis()) { stopSelf(); return; }
            final double rawLat = SharedPrefsUtil.getDouble(p, "lat", Double.NaN);
            final double rawLng = SharedPrefsUtil.getDouble(p, "lng", Double.NaN);
            if (!Inputs.validLat(rawLat) || !Inputs.validLng(rawLng)) {
                Log.w(TAG, "resume rejected: lat/lng missing or out of range");
                stopSelf();
                return;
            }
            final double lat = Inputs.clampLat(rawLat);
            final double lng = Inputs.clampLng(rawLng);
            final double dLat = Inputs.sanitizeDelta(SharedPrefsUtil.getDouble(p, "dLat", 0d));
            final double dLng = Inputs.sanitizeDelta(SharedPrefsUtil.getDouble(p, "dLng", 0d));
            int freqSeconds = p.getInt("mockFrequency", 10);
            if (freqSeconds <= 0) freqSeconds = 1;
            final long freqMillis = Inputs.clampFreqMs(freqSeconds * 1000L);
            final int count = Inputs.clampCount(p.getInt("mockCount", 0));
            final float speed = p.getBoolean("mockSpeed", true) ? 0.01f : 0f;
            startMockedService(lng, lat, dLng / 1_000_000d, dLat / 1_000_000d, freqMillis, count, speed);
        } catch (Throwable t) {
            Log.e(TAG, "resumeFromPrefsIfActive failed", t);
            stopSelf();
        }
    }

    /** Core entry point: register test providers + schedule periodic pushes. */
    protected void startMockedService(double longitude, double latitude,
                                       double longitudeDistance, double latitudeDistance,
                                       long mockMilli, int maxTime, float mockSpeed) {
        try {
            stopMockNow();
            attachAllProviders();                                                        // FIX-004
            // Reset progress counters. maxTime==0 means infinite — render indeterminate.
            totalTicks.set(maxTime == 0 ? Integer.MAX_VALUE : maxTime);
            doneTicks.set(0);
            final TimerTask t = new MockedLocationTask(this,
                    longitude, latitude, longitudeDistance, latitudeDistance, maxTime, mockSpeed);
            timer.schedule(t, 0L, mockMilli);
            tasks.add(t);
            mockState.postValue(MockState.MOCKED);
        } catch (SecurityException e) {
            Log.e(TAG, "Could not construct mock location providers", e);
            mockState.postValue(MockState.MOCK_ERROR);
            try {
                final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NotificationFactory.NOTIFICATION_ID + 1, NotificationFactory.buildError(this));
            } catch (Throwable ignored) {}
        }
    }

    private void stopMockNow() {
        for (TimerTask t : tasks) t.cancel();
        tasks.clear();
        for (MockedLocationProvider p : providers) p.shutdown();
        providers.clear();
        mockState.postValue(MockState.NOT_MOCKED);
    }

    void publishLocation(@NonNull Location value, double lat, double lng) {
        mockedLocation.postValue(value);
        if (totalTicks.get() != Integer.MAX_VALUE) doneTicks.incrementAndGet();
        refreshNotification(value);
        for (MockedLocationProvider prov : providers) prov.pushLocation(lat, lng);
    }

    void notifyMockCompleted() {
        mockState.postValue(MockState.NOT_MOCKED);
        stopSelf();
    }

    /** FIX-021 (V19): pre-33 path gated on signature-level custom permission to block
     *  cross-app broadcast injection. On 33+ use RECEIVER_NOT_EXPORTED constant directly.
     *  The pre-33 4-arg form has no export flag (UnspecifiedRegisterReceiverFlag), but the
     *  signature-protected permission is the equivalent control: only same-signature callers
     *  can deliver the broadcast, equivalent to NOT_EXPORTED for unprivileged senders. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerStopReceiver() {
        stopReceiver = new StopReceiver(this);
        final IntentFilter f = new IntentFilter(ACTION_STOP);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(stopReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(stopReceiver, f, STOP_MOCK_PERMISSION, null);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to register STOP receiver", t);
        }
    }

    private static final String STOP_MOCK_PERMISSION = "cl.coders.faketraveler.permission.STOP_MOCK";

    private void unregisterStopReceiver() {
        if (stopReceiver != null) {
            try { unregisterReceiver(stopReceiver); } catch (Throwable ignored) {}
            stopReceiver = null;
        }
    }

    public static class MockedBinder extends Binder {
        @NonNull private final MockedLocationService service;
        @NonNull public final LiveData<MockState> mockState;
        @NonNull public final LiveData<Location> mockedLocation;

        public MockedBinder(@NonNull MockedLocationService service) {
            this.service = service;
            this.mockState = service.mockState;
            this.mockedLocation = service.mockedLocation;
        }
        public void continueMock() { service.mockState.postValue(MockState.SERVICE_BOUND); }
        public void startMock(double longitude, double latitude,
                              double longitudeDistance, double latitudeDistance,
                              long mockMilli, int maxTimes, float mockSpeed) {
            service.startMockedService(longitude, latitude, longitudeDistance, latitudeDistance,
                    mockMilli, maxTimes, mockSpeed);
        }
        public void requestStop() { service.stopMockNow(); service.stopSelf(); }
    }

    static class StopReceiver extends BroadcastReceiver {
        @NonNull private final MockedLocationService svc;
        StopReceiver(@NonNull MockedLocationService s) { this.svc = s; }
        @Override public void onReceive(Context c, Intent i) {
            if (i == null || !ACTION_STOP.equals(i.getAction())) return;
            svc.stopMockNow();
            svc.stopSelf();
        }
    }
}
