package cl.coders.faketraveler;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Module 6: batch GPS-EXIF strip on a WorkManager thread to avoid ANR. One-shot (enqueued by the
 * UI's "Scan & clean now"); a requiresStorageNotLow constraint mirrors the spec. Honours
 * {@link FeatureFlag#EXIF_CLEANER}.
 */
public class ExifCleanWorker extends Worker {

    private static final String TAG = "ExifCleanWorker";
    private static final String UNIQUE_WORK = "cl.coders.faketraveler.exif_clean";
    private static final int BATCH_LIMIT = 200;

    public ExifCleanWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override
    public Result doWork() {
        final Context ctx = getApplicationContext();
        if (!FeatureFlag.EXIF_CLEANER.isEnabled(ctx)) return Result.success();
        try {
            new ExifCleaner(ctx).cleanAll(BATCH_LIMIT);
            return Result.success();
        } catch (Throwable t) {
            Log.e(TAG, "exif clean batch failed", t);
            return Result.retry();
        }
    }

    public static void enqueue(@NonNull Context ctx) {
        final Constraints constraints = new Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build();
        final OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(ExifCleanWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(ctx).enqueueUniqueWork(
                UNIQUE_WORK, ExistingWorkPolicy.KEEP, req);
    }
}
