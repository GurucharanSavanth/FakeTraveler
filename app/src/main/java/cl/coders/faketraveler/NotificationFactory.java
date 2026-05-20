package cl.coders.faketraveler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Locale;

/** Factory for the ongoing mock-location notification. FIX-006. */
public final class NotificationFactory {

    public static final String CHANNEL_ID = "faketraveler.mock";
    public static final int NOTIFICATION_ID = 1729;

    private NotificationFactory() {
        throw new UnsupportedOperationException();
    }

    /** Creates the notification channel on API 26+; no-op below. Idempotent. */
    public static void ensureChannel(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        final NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        final NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.Notification_ChannelName),
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(ctx.getString(R.string.Notification_ChannelDesc));
        ch.setShowBadge(false);
        ch.enableVibration(false);
        try {
            nm.createNotificationChannel(ch);
        } catch (Throwable ignored) {
            // some manufacturers throw if channel exists with different importance
        }
    }

    /**
     * Build the ongoing notification shown while the service mocks.
     *
     * @param ctx application context
     * @param loc current mocked location (may be null when service starts before first push)
     */
    @NonNull
    public static Notification buildOngoing(@NonNull Context ctx, @Nullable Location loc) {
        ensureChannel(ctx);

        final int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        final Intent stop = new Intent(MockedLocationService.ACTION_STOP).setPackage(ctx.getPackageName());
        final PendingIntent stopPi = PendingIntent.getBroadcast(ctx, 0, stop, piFlags);

        final Intent open = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        final PendingIntent openPi = PendingIntent.getActivity(ctx, 1, open, piFlags);

        final String body = loc == null
                ? ctx.getString(R.string.Notification_BodyIdle)
                : ctx.getString(
                        R.string.Notification_BodyMocking,
                        formatCoord(loc.getLatitude()),
                        formatCoord(loc.getLongitude()));

        final NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mock_notification)
                .setContentTitle(ctx.getString(R.string.Notification_Title))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET) // hide coords from lockscreen
                .setContentIntent(openPi)
                .addAction(0, ctx.getString(R.string.Notification_Stop), stopPi)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    @NonNull
    public static Notification buildError(@NonNull Context ctx) {
        ensureChannel(ctx);
        final int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        final Intent open = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        final PendingIntent openPi = PendingIntent.getActivity(ctx, 2, open, piFlags);

        return new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mock_notification)
                .setContentTitle(ctx.getString(R.string.Notification_ErrorTitle))
                .setContentText(ctx.getString(R.string.Notification_ErrorBody))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(ctx.getString(R.string.Notification_ErrorBody)))
                .setOngoing(false)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openPi)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build();
    }

    @NonNull
    private static String formatCoord(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }
}
