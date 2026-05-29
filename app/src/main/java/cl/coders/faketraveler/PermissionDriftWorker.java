package cl.coders.faketraveler;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * Module 5: periodic permission-drift scan. Uses the project's chained one-shot pattern (mirrors
 * {@code HealthCheckWorker}) rather than PeriodicWorkRequest: each run reschedules the next ~24 h out
 * with a requiresBatteryNotLow constraint, so cadence stays in lockstep with WorkManager's policy.
 * Posts a high-importance notification when the scan raises new critical alerts.
 */
public class PermissionDriftWorker extends Worker {

    private static final String TAG = "PermissionDriftWorker";
    private static final String UNIQUE_WORK = "cl.coders.faketraveler.permission_drift";
    private static final String CHANNEL_ID = "permission_drift_alerts";
    private static final int NOTIFICATION_ID = 7201;
    private static final long INTERVAL_HOURS = 24L;

    public PermissionDriftWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override
    public Result doWork() {
        final Context ctx = getApplicationContext();
        try {
            if (FeatureFlag.PERMISSION_DRIFT.isEnabled(ctx)) {
                final int newCritical = new PermissionScanner(ctx).scan();
                if (newCritical > 0) notifyCritical(ctx, newCritical);
            }
        } catch (Throwable t) {
            Log.e(TAG, "permission drift scan failed", t);
        } finally {
            scheduleNext(ctx); // chained reschedule regardless of outcome
        }
        return Result.success();
    }

    /** Enqueue the next scan ~24 h out. KEEP so a pending tick is never duplicated. */
    public static void scheduleNext(@NonNull Context ctx) {
        final Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();
        final OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(PermissionDriftWorker.class)
                .setInitialDelay(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(ctx).enqueueUniqueWork(
                UNIQUE_WORK, ExistingWorkPolicy.KEEP, req);
    }

    public static void cancel(@NonNull Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_WORK);
    }

    private void notifyCritical(@NonNull Context ctx, int count) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return; // user has not granted notification permission
        }
        final NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = nm.getNotificationChannel(CHANNEL_ID);
            if (ch == null) {
                ch = new NotificationChannel(CHANNEL_ID,
                        ctx.getString(R.string.PermissionDrift_Channel),
                        NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
        }
        final NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_permission_drift)
                .setContentTitle(ctx.getString(R.string.PermissionDrift_Notif_Title))
                .setContentText(ctx.getResources().getQuantityString(
                        R.plurals.PermissionDrift_Notif_Body, count, count))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        try {
            nm.notify(NOTIFICATION_ID, b.build());
        } catch (Throwable ignored) {
        }
    }
}
