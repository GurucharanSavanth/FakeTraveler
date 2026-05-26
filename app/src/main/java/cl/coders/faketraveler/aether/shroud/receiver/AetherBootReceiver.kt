package cl.coders.faketraveler.aether.shroud.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import cl.coders.faketraveler.SharedPrefsUtil
import cl.coders.faketraveler.aether.shroud.worker.AetherBootWorker
import java.util.concurrent.TimeUnit

/**
 * Aether v2 boot receiver. Listens for BOOT_COMPLETED, LOCKED_BOOT_COMPLETED,
 * and MY_PACKAGE_REPLACED to re-establish mock location after reboot or app update.
 *
 * Delegates to [AetherBootWorker] via WorkManager instead of starting a foreground
 * service directly -- avoids OEM BackgroundServiceStartNotAllowedException.
 */
class AetherBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        try {
            if (!SharedPrefsUtil.isRestoreAfterBoot(context)) {
                Log.d(TAG, "restoreAfterBoot disabled; skipping boot resume")
                return
            }

            val request = OneTimeWorkRequest.Builder(AetherBootWorker::class.java)
                .setInitialDelay(BOOT_DELAY_SECONDS, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)

            Log.i(TAG, "Enqueued AetherBootWorker for action=$action")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to enqueue AetherBootWorker", t)
        }
    }

    companion object {
        private const val TAG = "AetherBootReceiver"
        private const val WORK_NAME = "aether_boot_resume"
        private const val BOOT_DELAY_SECONDS = 10L
    }
}
