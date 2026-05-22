package cl.coders.faketraveler.joystick;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import cl.coders.faketraveler.MainActivity;
import cl.coders.faketraveler.MockLogger;
import cl.coders.faketraveler.MockedLocationProvider;
import cl.coders.faketraveler.MockedLocationService;
import cl.coders.faketraveler.NotificationFactory;
import cl.coders.faketraveler.PermissionChecker;
import cl.coders.faketraveler.R;
import cl.coders.faketraveler.SharedPrefsUtil;
import cl.coders.faketraveler.util.Inputs;

/**
 * Foreground service that owns the floating {@link JoystickView} overlay, the speed/stop
 * controls, and the periodic tick that advances the mock location through real
 * {@link MockedLocationProvider} test providers.
 *
 * <p>{@link #onDestroy()} shuts the scheduler down synchronously and tears down the test
 * providers so no zombie locations get pushed after the user stops the joystick (V36).
 */
public class JoystickService extends Service implements JoystickView.OnMoveListener {

    @NonNull private static final String TAG = JoystickService.class.getSimpleName();
    private static final long TICK_MS = 1_000L;
    public static final int NOTIFICATION_ID = 7777;
    public static final String ACTION_STOP = "cl.coders.faketraveler.joystick.action.STOP";

    @Nullable private View overlay;
    @Nullable private WindowManager wm;
    @NonNull private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    @NonNull private final AtomicReference<JoystickEngine.SpeedMode> speedMode =
            new AtomicReference<>(JoystickEngine.SpeedMode.WALK);
    @NonNull private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    /** CopyOnWriteArrayList: scheduler thread iterates while main thread mutates on destroy. */
    @NonNull private final List<MockedLocationProvider> providers = new CopyOnWriteArrayList<>();

    private volatile double curLat;
    private volatile double curLng;
    private volatile boolean providersReady = false;

    @Nullable private BroadcastReceiver stopReceiver;
    private static final String STOP_MOCK_PERMISSION = "cl.coders.faketraveler.permission.STOP_MOCK";

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!startForegroundCompat()) {
            // FGS type=location requires ACCESS_COARSE/FINE_LOCATION on API 34+. Without it,
            // startForeground throws SecurityException and crashes the process. Bail cleanly.
            stopSelf();
            return;
        }
        if (!PermissionChecker.isMockLocationEnabled(this)) {
            Toast.makeText(this, R.string.Joystick_NeedMockPermission, Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        loadOrigin();
        if (!attachProviders()) {
            Toast.makeText(this, R.string.Joystick_ProviderFailed, Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        registerStopReceiver();
        addOverlay();
        // Push initial location so target apps see the joystick origin immediately rather
        // than waiting for the first user input.
        pushCurrentLocation();
        // Fixed-delay (not fixed-rate) so backed-up ticks don't fire in bursts when the
        // process emerges from cached state.
        scheduler.scheduleWithFixedDelay(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        MockLogger.log("joystick_start",
                String.format(Locale.US, "origin=%.6f,%.6f", curLat, curLng));
    }

    private boolean startForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification());
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "startForeground failed", t);
            MockLogger.log("joystick_error", "fgs_denied");
            return false;
        }
    }

    private android.app.Notification buildNotification() {
        final Location loc = new Location(LocationManager.GPS_PROVIDER);
        loc.setLatitude(curLat);
        loc.setLongitude(curLng);
        return NotificationFactory.buildOngoing(this, loc);
    }

    private void loadOrigin() {
        final SharedPreferences sp = getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
        curLat = SharedPrefsUtil.getDouble(sp, "lat", 0d);
        curLng = SharedPrefsUtil.getDouble(sp, "lng", 0d);
        if (!Inputs.validLat(curLat) || !Inputs.validLng(curLng)) {
            curLat = 0d;
            curLng = 0d;
        }
    }

    private boolean attachProviders() {
        try {
            providers.add(new MockedLocationProvider(LocationManager.GPS_PROVIDER, this));
            providers.add(new MockedLocationProvider(LocationManager.NETWORK_PROVIDER, this));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                providers.add(new MockedLocationProvider(LocationManager.FUSED_PROVIDER, this));
            }
            providersReady = true;
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "addTestProvider failed", e);
            MockLogger.log("joystick_error", "provider_security");
            tearDownProviders();
            return false;
        }
    }

    private void tearDownProviders() {
        for (MockedLocationProvider p : providers) p.shutdown();
        providers.clear();
        providersReady = false;
    }

    /** Listen for the same ACTION_STOP broadcast used by the timed mock so the user can
     *  stop the joystick from the existing notification action without a parallel UI. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerStopReceiver() {
        stopReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (i != null && MockedLocationService.ACTION_STOP.equals(i.getAction())) {
                    stopSelf();
                }
            }
        };
        final IntentFilter f = new IntentFilter(MockedLocationService.ACTION_STOP);
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

    private void unregisterStopReceiver() {
        if (stopReceiver != null) {
            try { unregisterReceiver(stopReceiver); } catch (Throwable ignored) {}
            stopReceiver = null;
        }
    }

    private void addOverlay() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        // No parent ViewGroup exists yet — WindowManager.addView is the parent. Passing
        // null is correct here, but lint flags it generically; suppress at the call site.
        @android.annotation.SuppressLint("InflateParams")
        final View root = LayoutInflater.from(this).inflate(R.layout.joystick_overlay, null);
        overlay = root;
        JoystickView jv = Inputs.requireView(overlay, R.id.joystick_view, "joystick_view");
        jv.setOnMoveListener(this);
        wireSpeedButton(overlay, R.id.joystick_btn_walk, JoystickEngine.SpeedMode.WALK);
        wireSpeedButton(overlay, R.id.joystick_btn_bike, JoystickEngine.SpeedMode.BIKE);
        wireSpeedButton(overlay, R.id.joystick_btn_drive, JoystickEngine.SpeedMode.DRIVE);
        final Button stop = Inputs.requireView(overlay, R.id.joystick_btn_stop, "joystick_btn_stop");
        stop.setOnClickListener(v -> stopSelf());

        @SuppressWarnings("deprecation")  // TYPE_PHONE is the only overlay type on pre-O
        final int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        try {
            wm.addView(overlay, lp);
        } catch (Throwable t) {
            Log.e(TAG, "addView failed", t);
            MockLogger.log("joystick_error", "addView_failed");
            stopSelf();
        }
    }

    private void wireSpeedButton(@NonNull View root, int id, @NonNull JoystickEngine.SpeedMode mode) {
        final Button b = Inputs.requireView(root, id, "speed_btn");
        b.setOnClickListener(v -> {
            speedMode.set(mode);
            MockLogger.log("joystick_speed", mode.name());
        });
    }

    private void tick() {
        if (!providersReady) return;
        final State s = state.get();
        if (s == null) return;
        if (s.magnitude > 0d) {
            final JoystickEngine.SpeedMode sm = speedMode.get();
            final double effectiveMs = sm.metersPerSecond * s.magnitude;
            final JoystickEngine.LatLng next = JoystickEngine.advance(
                    curLat, curLng, s.angleRad, effectiveMs, TICK_MS);
            curLat = Inputs.clampLat(next.lat);
            curLng = Inputs.clampLng(next.lng);
        }
        pushCurrentLocation();
    }

    private void pushCurrentLocation() {
        // Snapshot to locals so the Timer thread sees consistent values even if onMove fires
        // mid-iteration.
        final double lat = curLat;
        final double lng = curLng;
        final List<MockedLocationProvider> snapshot = new ArrayList<>(providers);
        for (MockedLocationProvider p : snapshot) p.pushLocation(lat, lng);
        persistLastLocation(lat, lng);
    }

    private void persistLastLocation(double lat, double lng) {
        try {
            final Location loc = new Location(LocationManager.GPS_PROVIDER);
            loc.setLatitude(lat);
            loc.setLongitude(lng);
            SharedPrefsUtil.saveLastMockedLocation(getApplicationContext(), loc);
        } catch (Throwable t) {
            Log.w(TAG, "persistLastLocation failed", t);
        }
    }

    @Override
    public void onMove(double angleRad, double magnitude) {
        state.set(new State(angleRad, magnitude));
    }

    @Override
    public void onRelease() {
        state.set(State.IDLE);
    }

    @Override
    public void onDestroy() {
        // V36 — shutdown synchronously: no zombie ticks after the overlay is gone.
        scheduler.shutdownNow();
        unregisterStopReceiver();
        try {
            if (wm != null && overlay != null) wm.removeView(overlay);
        } catch (Throwable t) {
            Log.w(TAG, "removeView failed", t);
        }
        tearDownProviders();
        MockLogger.log("joystick_stop", "overlay removed");
        super.onDestroy();
    }

    /** Build a PendingIntent that calls {@link #ACTION_STOP} on this service. Other modules
     *  (notification actions, quick settings) can use this to stop the joystick remotely. */
    @NonNull
    public static PendingIntent stopPendingIntent(@NonNull Context ctx) {
        final Intent i = new Intent(ctx, JoystickService.class).setAction(ACTION_STOP);
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(ctx, 0, i, flags);
    }

    private static final class State {
        static final State IDLE = new State(0.0, 0.0);
        final double angleRad;
        final double magnitude;
        State(double a, double m) { angleRad = a; magnitude = m; }
    }
}
