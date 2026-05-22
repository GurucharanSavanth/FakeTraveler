package cl.coders.faketraveler.joystick;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import cl.coders.faketraveler.MockLogger;
import cl.coders.faketraveler.NotificationFactory;
import cl.coders.faketraveler.R;
import cl.coders.faketraveler.util.Inputs;

/**
 * Foreground service that owns the floating {@link JoystickView} overlay and a 1 Hz tick
 * that would advance the mock location.
 *
 * <p>{@link #onDestroy()} shuts the scheduler down synchronously: leaving a tick scheduled
 * after the overlay is removed would push locations the user thought they had stopped
 * (V36).
 */
public class JoystickService extends Service implements JoystickView.OnMoveListener {

    @NonNull private static final String TAG = JoystickService.class.getSimpleName();
    private static final long TICK_MS = 1_000L;
    public static final int NOTIFICATION_ID = 7777;

    @Nullable private View overlay;
    @Nullable private WindowManager wm;
    @NonNull private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    @NonNull private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat();
        addOverlay();
        // Fixed-delay (not fixed-rate) so backed-up ticks don't fire in bursts when the
        // process emerges from cached state.
        scheduler.scheduleWithFixedDelay(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        MockLogger.log("joystick_start", "overlay added");
    }

    private void startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID,
                    NotificationFactory.buildOngoing(this, null),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, NotificationFactory.buildOngoing(this, null));
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
        @SuppressWarnings("deprecation")  // TYPE_PHONE is the only overlay type on pre-O
        final int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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

    private void tick() {
        State s = state.get();
        if (s == null || s == State.IDLE) return;
        // The advance() output is not yet forwarded to a live MockedLocationProvider —
        // that requires extending the service's binder contract. Until then, the tick is
        // observable through the debug console only.
        MockLogger.log("joystick_tick",
                String.format(Locale.US, "ang=%.2f mag=%.2f", s.angleRad, s.magnitude));
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
        try {
            if (wm != null && overlay != null) wm.removeView(overlay);
        } catch (Throwable t) {
            Log.w(TAG, "removeView failed", t);
        }
        MockLogger.log("joystick_stop", "overlay removed");
        super.onDestroy();
    }

    private static final class State {
        static final State IDLE = new State(0.0, 0.0);
        final double angleRad;
        final double magnitude;
        State(double a, double m) { angleRad = a; magnitude = m; }
    }
}
