package cl.coders.faketraveler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import java.util.concurrent.TimeUnit;

/**
 * Receives BOOT_COMPLETED / LOCKED_BOOT_COMPLETED and hands off to {@link BootResumeWorker}.
 *
 * <p>The previous implementation called {@code startForegroundService} from inside
 * {@code onReceive}; that pattern is fragile against OEM background-start restrictions and
 * the direct-boot window. Delegating to WorkManager makes the resume attempt retriable
 * with backoff (V31).
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    @NonNull
    private static final String TAG = BootCompletedReceiver.class.getSimpleName();

    @Override
    public void onReceive(@NonNull Context ctx, @NonNull Intent intent) {
        final String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            return;
        }
        try {
            final OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(BootResumeWorker.class)
                    .setInitialDelay(10L, TimeUnit.SECONDS)
                    .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            WorkRequest.MIN_BACKOFF_MILLIS,
                            TimeUnit.MILLISECONDS)
                    .build();
            WorkManager.getInstance(ctx)
                    .enqueueUniqueWork("boot_resume", ExistingWorkPolicy.REPLACE, req);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to enqueue boot resume worker", t);
        }
    }
}
