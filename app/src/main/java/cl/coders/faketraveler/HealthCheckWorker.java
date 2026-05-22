package cl.coders.faketraveler;

import android.app.NotificationManager;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * Heartbeat for the active mock. Runs every {@link #INTERVAL} via self re-enqueue while a
 * mock is supposed to be live. Verifies that:
 *
 * <ol>
 *   <li>Mock-location permission is still granted by the system.</li>
 *   <li>The GPS provider is still enabled.</li>
 *   <li>Battery optimisations are not interfering (warning only — does not fail).</li>
 * </ol>
 *
 * <p>On a failure the worker writes a high-priority notification deep-linking to developer
 * settings and kicks off {@link AutoRecoveryWorker}. Every failure path also calls
 * {@link MockLogger#log(String, String)} so the debug console can show silent retries
 * (V32).
 */
public class HealthCheckWorker extends Worker {

    @NonNull private static final String TAG = HealthCheckWorker.class.getSimpleName();
    @NonNull public static final String UNIQUE_NAME = "health";
    private static final long INTERVAL_SECONDS = 30L;

    public HealthCheckWorker(@NonNull Context ctx, @NonNull WorkerParameters p) {
        super(ctx, p);
    }

    @NonNull
    @Override
    public Result doWork() {
        final Context ctx = getApplicationContext();
        try {
            if (!PermissionChecker.isMockLocationEnabled(ctx)) {
                return failWith(ctx, "mock_location_disabled");
            }
            final LocationManager lm =
                    (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                return failWith(ctx, "gps_provider_off");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                final PowerManager pm =
                        (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(ctx.getPackageName())) {
                    // Warning, not failure — the mock often survives this on lenient OEMs.
                    MockLogger.log("health_warn", "battery_optimization_on");
                }
            }
            scheduleNext(ctx);
            return Result.success();
        } catch (Throwable t) {
            Log.e(TAG, "health check threw", t);
            return Result.retry();
        }
    }

    // Re-enqueues self from doWork(); explicit overload keeps lint quiet about the
    // (long, TimeUnit) signature being preferred over the Duration variant.

    @NonNull
    private Result failWith(@NonNull Context ctx, @NonNull String reason) {
        MockLogger.log("health_fail", reason);
        try {
            final NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NotificationFactory.HEALTH_NOTIFICATION_ID,
                        NotificationFactory.buildHealthFailure(ctx, reason));
            }
        } catch (Throwable t) {
            Log.w(TAG, "health notif failed", t);
        }
        WorkManager.getInstance(ctx).enqueueUniqueWork(
                AutoRecoveryWorker.UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(AutoRecoveryWorker.class)
                        .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                10_000L,
                                TimeUnit.MILLISECONDS)
                        .build());
        // Keep polling — the mock may recover and we want to detect that too.
        scheduleNext(ctx);
        return Result.retry();
    }

    /** Enqueues the next health check tick. {@link ExistingWorkPolicy#REPLACE} so callers
     *  starting a fresh mock cleanly restart the heartbeat. */
    public static void scheduleNext(@NonNull Context ctx) {
        final OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(HealthCheckWorker.class)
                .setInitialDelay(INTERVAL_SECONDS, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(ctx)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, req);
    }

    /** Stops the heartbeat <em>and</em> any pending auto-recovery work. Both must be
     *  cancelled when the user stops the mock; otherwise a recovery attempt could
     *  resurrect the mock after an explicit stop. */
    public static void cancel(@NonNull Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_NAME);
        WorkManager.getInstance(ctx).cancelUniqueWork(AutoRecoveryWorker.UNIQUE_NAME);
    }
}
