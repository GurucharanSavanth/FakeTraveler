package cl.coders.faketraveler;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;


/**
 * Re-issues {@code ACTION_RESUME} to {@link MockedLocationService} after a reboot.
 *
 * <p>The {@link BootCompletedReceiver} enqueues this worker rather than starting the
 * foreground service directly. That indirection matters because BOOT_COMPLETED can fire
 * while the device is still in direct-boot or user-locked state, and several aggressive
 * OEMs throw {@code BackgroundServiceStartNotAllowedException} when a receiver tries to
 * start a foreground service at that moment. WorkManager defers + retries with backoff.
 *
 * <p>Side effect: invokes {@code Context.startForegroundService} (or pre-O {@code
 * startService}) with the same intent the in-app stop receiver listens for.
 *
 * <p>Invariant: V31 (no direct service start from receiver).
 */
public class BootResumeWorker extends Worker {

    @NonNull private static final String TAG = BootResumeWorker.class.getSimpleName();

    public BootResumeWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        final Context ctx = getApplicationContext();
        try {
            if (!SharedPrefsUtil.isRestoreAfterBoot(ctx)) return Result.success();

            final SharedPreferences p =
                    ctx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
            final long endTime = p.getLong("endTime", 0L);
            if (endTime <= System.currentTimeMillis()) return Result.success();

            if (!PermissionChecker.isMockLocationEnabled(ctx)) {
                Log.w(TAG, "boot_resume: mock location no longer enabled");
                return Result.success();
            }

            final Intent svc = new Intent(ctx, MockedLocationService.class)
                    .setAction(MockedLocationService.ACTION_RESUME);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(ctx, svc);
            } else {
                ctx.startService(svc);
            }
            return Result.success();
        } catch (Throwable t) {
            Log.e(TAG, "boot_resume failed", t);
            return Result.retry();
        }
    }
}
