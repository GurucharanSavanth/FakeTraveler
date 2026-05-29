package cl.coders.faketraveler;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * Module 7: optional scheduled wipe. Off unless the user enables {@code privacyWipeScheduled}; even
 * then it never touches app data/prefs on a schedule — only the data-trace categories (sessions,
 * geofence events, permission snapshots, EXIF log). Chained one-shot at {@code privacyWipeIntervalDays}
 * (default 7) with deviceIdle + batteryNotLow constraints.
 */
public class PrivacyWipeWorker extends Worker {

    private static final String TAG = "PrivacyWipeWorker";
    private static final String UNIQUE_WORK = "cl.coders.faketraveler.privacy_wipe";
    public static final String PREF_SCHEDULED = "privacyWipeScheduled";
    public static final String PREF_INTERVAL_DAYS = "privacyWipeIntervalDays";

    public PrivacyWipeWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override
    public Result doWork() {
        final Context ctx = getApplicationContext();
        final SharedPreferences sp =
                ctx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
        if (!FeatureFlag.PRIVACY_WIPE.isEnabled(ctx) || !sp.getBoolean(PREF_SCHEDULED, false)) {
            return Result.success(); // disabled — do not reschedule
        }
        try {
            final PrivacyWipeEngine.Options o = new PrivacyWipeEngine.Options();
            o.sessionHistory = true;
            o.geofenceEvents = true;
            o.permissions = true;
            o.exifBackups = true;
            new PrivacyWipeEngine(ctx).wipe(o);
        } catch (Throwable t) {
            Log.e(TAG, "scheduled wipe failed", t);
        } finally {
            scheduleNext(ctx);
        }
        return Result.success();
    }

    /** Enqueue the next scheduled wipe based on the user's interval preference. */
    public static void scheduleNext(@NonNull Context ctx) {
        final SharedPreferences sp =
                ctx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
        if (!sp.getBoolean(PREF_SCHEDULED, false)) {
            cancel(ctx);
            return;
        }
        final int days = Math.max(1, sp.getInt(PREF_INTERVAL_DAYS, 7));
        final Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)
                .build();
        final OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(PrivacyWipeWorker.class)
                .setInitialDelay(days, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(ctx).enqueueUniqueWork(
                UNIQUE_WORK, ExistingWorkPolicy.REPLACE, req);
    }

    public static void cancel(@NonNull Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_WORK);
    }
}
