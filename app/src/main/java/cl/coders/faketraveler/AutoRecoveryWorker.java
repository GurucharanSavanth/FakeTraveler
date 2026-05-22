package cl.coders.faketraveler;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Best-effort restart of the mock loop after {@link HealthCheckWorker} reports a failure.
 *
 * <p>Retries up to {@link #MAX_RETRY} times with exponential backoff (10 s / 30 s / 90 s).
 * After that the worker gives up — the high-priority notification posted by the health
 * check already routes the user to developer settings, so further silent retries would
 * only obscure the underlying problem.
 */
public class AutoRecoveryWorker extends Worker {

    @NonNull private static final String TAG = AutoRecoveryWorker.class.getSimpleName();
    @NonNull public static final String UNIQUE_NAME = "auto_recovery";
    private static final int MAX_RETRY = 3;

    public AutoRecoveryWorker(@NonNull Context ctx, @NonNull WorkerParameters p) {
        super(ctx, p);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (getRunAttemptCount() >= MAX_RETRY) {
            MockLogger.log("auto_recovery_gave_up", "max_retries=" + MAX_RETRY);
            return Result.success();
        }
        try {
            final Context ctx = getApplicationContext();
            final Intent svc = new Intent(ctx, MockedLocationService.class)
                    .setAction(MockedLocationService.ACTION_RESUME);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(ctx, svc);
            } else {
                ctx.startService(svc);
            }
            MockLogger.log("auto_recovery_attempt", "n=" + (getRunAttemptCount() + 1));
            return Result.retry();
        } catch (Throwable t) {
            Log.e(TAG, "auto_recovery threw", t);
            return Result.retry();
        }
    }
}
