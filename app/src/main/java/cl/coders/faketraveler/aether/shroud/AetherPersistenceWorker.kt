package cl.coders.faketraveler.aether.shroud

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * L3 persistence layer: WorkManager periodic worker that re-arms [AetherService]
 * if all higher-priority persistence layers (L1 foreground, L2 alarm) have failed.
 *
 * Enqueued as a 15-minute periodic expedited work request by [AetherService.armWorkManager].
 * On each execution, starts the service with [AetherService.ACTION_RESUME].
 */
class AetherPersistenceWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val intent = Intent(applicationContext, AetherService::class.java).apply {
                action = AetherService.ACTION_RESUME
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            Log.d(TAG, "L3 heartbeat: re-armed AetherService")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "L3 heartbeat failed", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AetherPersistenceWorker"
    }
}
