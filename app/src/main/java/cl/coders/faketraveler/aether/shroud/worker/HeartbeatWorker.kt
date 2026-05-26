package cl.coders.faketraveler.aether.shroud.worker

import android.app.NotificationManager
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cl.coders.faketraveler.MockLogger
import cl.coders.faketraveler.NotificationFactory
import cl.coders.faketraveler.PermissionChecker
import java.util.concurrent.TimeUnit

/**
 * Aether v2 heartbeat worker. Periodically verifies mock-location health:
 *
 * 1. Mock-location permission is still granted.
 * 2. GPS provider is still enabled.
 * 3. Battery optimisation status (warning only).
 *
 * On failure, posts a high-priority health notification and kicks off
 * [RecoveryWorker]. Self-re-enqueues every 15 minutes.
 *
 * No `!!`, no `GlobalScope`, no `runBlocking`.
 */
class HeartbeatWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            // Check 1: mock-location permission
            if (!PermissionChecker.isMockLocationEnabled(ctx)) {
                return failWith(ctx, "mock_location_disabled")
            }

            // Check 2: GPS provider enabled
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                return failWith(ctx, "gps_provider_off")
            }

            // Check 3: battery optimisation (warning, not failure)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null && !pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                    MockLogger.log("health_warn", "battery_optimization_on")
                }
            }

            scheduleNext(ctx)
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Heartbeat check threw", t)
            Result.retry()
        }
    }

    /** Handle a health failure: log, notify user, enqueue [RecoveryWorker],
     *  and re-schedule the next heartbeat. */
    private fun failWith(ctx: Context, reason: String): Result {
        MockLogger.log("health_fail", reason)

        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(
                NotificationFactory.HEALTH_NOTIFICATION_ID,
                NotificationFactory.buildHealthFailure(ctx, reason)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Health notification failed", t)
        }

        // Kick off recovery
        try {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                RecoveryWorker.UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequest.Builder(RecoveryWorker::class.java).build()
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to enqueue RecoveryWorker", t)
        }

        scheduleNext(ctx)
        return Result.retry()
    }

    companion object {
        private const val TAG = "HeartbeatWorker"
        const val UNIQUE_NAME = "aether_heartbeat"
        private const val INTERVAL_SECONDS = 900L // 15 minutes

        /** Enqueue the next heartbeat tick. REPLACE so callers starting a fresh
         *  mock cleanly restart the heartbeat cadence. */
        fun scheduleNext(ctx: Context) {
            try {
                val request = OneTimeWorkRequest.Builder(HeartbeatWorker::class.java)
                    .setInitialDelay(INTERVAL_SECONDS, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(ctx)
                    .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to schedule next heartbeat", t)
            }
        }

        /** Cancel the heartbeat and any pending recovery work. Both must be
         *  cancelled when the user stops the mock to prevent resurrection. */
        fun cancel(ctx: Context) {
            try {
                WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_NAME)
                WorkManager.getInstance(ctx).cancelUniqueWork(RecoveryWorker.UNIQUE_NAME)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to cancel heartbeat/recovery", t)
            }
        }
    }
}
