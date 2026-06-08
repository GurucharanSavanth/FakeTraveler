package cl.coders.faketraveler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

/**
 * Reads radio / location-mode state while mocking and, when settings could let the system
 * override the mocked position (Wi-Fi scanning on, or Location mode not "Device only"), posts a
 * guidance notification that deep-links to the relevant system settings page.
 *
 * <p>Read-only by design: it never changes a system setting and never disables a radio. The user
 * decides whether to act on the guidance. The owning service may additionally self-stop its own
 * mock when the user has opted into Strict mode ({@link SharedPrefsUtil#KEY_STRICT_RADIO_MODE}).
 */
final class RadioConsistencyManager {

    @NonNull private static final String TAG = "RadioConsistency";
    @NonNull static final String CHANNEL_ID = "faketraveler.radio";
    static final int NOTIFICATION_ID = 4243;

    // Settings.Secure.LOCATION_MODE literals — the symbol is @Deprecated on API 28+ but its value
    // stays readable. Using literals mirrors PermissionChecker.OP_MOCK_LOCATION and avoids the
    // deprecation / InlinedApi lint hit (V21).
    @NonNull private static final String LOCATION_MODE_KEY = "location_mode";
    private static final int LOCATION_MODE_BATTERY_SAVING = 2;
    private static final int LOCATION_MODE_HIGH_ACCURACY = 3;

    private static final int FLAG_WIFI = 1;
    private static final int FLAG_LOC_MODE = 1 << 1;

    @NonNull private final Context appCtx;
    /** Last reported issue bitmask; -1 forces a fresh post on first run. Touched only on the
     *  single mock Timer thread, so no synchronization is needed. */
    private int lastMask = -1;

    RadioConsistencyManager(@NonNull Context ctx) {
        this.appCtx = ctx.getApplicationContext();
    }

    /**
     * Evaluate current radio / location-mode state and post or clear the guidance notification.
     * Re-posts only when the set of issues changes, so it never spams.
     *
     * @return {@code true} if any inconsistency is currently present
     */
    boolean checkAndNotify() {
        int mask = 0;
        if (wifiScanAlwaysOn()) mask |= FLAG_WIFI;
        if (locationModeOverrides()) mask |= FLAG_LOC_MODE;
        if (mask != lastMask) {
            lastMask = mask;
            if (mask == 0) clear();
            else post(mask);
        }
        return mask != 0;
    }

    private boolean wifiScanAlwaysOn() {
        try {
            final WifiManager wm = (WifiManager) appCtx.getSystemService(Context.WIFI_SERVICE);
            return wm != null && wm.isScanAlwaysAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean locationModeOverrides() {
        try {
            final int mode = Settings.Secure.getInt(appCtx.getContentResolver(), LOCATION_MODE_KEY, 0);
            return mode == LOCATION_MODE_HIGH_ACCURACY || mode == LOCATION_MODE_BATTERY_SAVING;
        } catch (Throwable t) {
            return false;
        }
    }

    private void post(int mask) {
        try {
            ensureChannel();
            final NotificationManager nm =
                    (NotificationManager) appCtx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            final StringBuilder body = new StringBuilder();
            if ((mask & FLAG_WIFI) != 0) body.append(appCtx.getString(R.string.RadioGuard_Body_Wifi));
            if ((mask & FLAG_LOC_MODE) != 0) {
                if (body.length() > 0) body.append('\n');
                body.append(appCtx.getString(R.string.RadioGuard_Body_LocMode));
            }
            final String text = body.toString();
            final int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
            final Intent settings = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final PendingIntent pi = PendingIntent.getActivity(appCtx, 7, settings, piFlags);
            final Notification n = new NotificationCompat.Builder(appCtx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_location_off)
                    .setContentTitle(appCtx.getString(R.string.RadioGuard_Title))
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setContentIntent(pi)
                    .addAction(0, appCtx.getString(R.string.RadioGuard_Action), pi)
                    .build();
            nm.notify(NOTIFICATION_ID, n);
        } catch (Throwable t) {
            Log.w(TAG, "radio guidance post failed", t);
        }
    }

    private void clear() {
        try {
            final NotificationManager nm =
                    (NotificationManager) appCtx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIFICATION_ID);
        } catch (Throwable ignored) {
            // notification manager unavailable; nothing to clear
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        final NotificationManager nm = appCtx.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        final NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                appCtx.getString(R.string.RadioGuard_ChannelName),
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(appCtx.getString(R.string.RadioGuard_ChannelDesc));
        try {
            nm.createNotificationChannel(ch);
        } catch (Throwable ignored) {
            // some manufacturers throw if a channel exists with different importance
        }
    }
}
