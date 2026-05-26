package cl.coders.faketraveler.aether.shroud.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cl.coders.faketraveler.MockLogger
import cl.coders.faketraveler.MockedLocationService
import java.util.concurrent.TimeUnit

/**
 * Aether v2 recovery worker. Best-effort restart of the mock loop after
 * [HeartbeatWorker] detects a failure.
 *
 * Retries up to [MAX_RETRY] times with exponential backoff (10 s / 20 s / 40 s).
 * After exhausting retries, gives up -- the health notification already informed
 * the user, so further silent restarts would only obscure the root cause.
 *
 * No `!!`, no `GlobalScope`, no `runBlocking`.
 */
class RecoveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (runAttemptCount >= MAX_RETRY) {
            MockLogger.log("aether_recovery_gave_up", "max_retries=$MAX_RETRY")
            Log.w(TAG, "Gave up after $MAX_RETRY attempts")
            return Result.success()
        }

        return try {
            val ctx = applicationContext
            val svc = Intent(ctx, MockedLocationService::class.java).apply {
                action = MockedLocationService.ACTION_RESUME
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(ctx, svc)
            } else {
                ctx.startService(svc)
            }

            MockLogger.log("aether_recovery_attempt", "n=${runAttemptCount + 1}")
            Log.i(TAG, "Recovery attempt ${runAttemptCount + 1}")
            Result.retry()
        } catch (t: Throwable) {
            Log.e(TAG, "Recovery attempt threw", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "RecoveryWorker"
        const val UNIQUE_NAME = "aether_recovery"
        private const val MAX_RETRY = 3

        /** Enqueue a recovery attempt with exponential backoff. */
        fun enqueue(ctx: Context) {
            try {
                val request = OneTimeWorkRequest.Builder(RecoveryWorker::class.java)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        10_000L,
                        TimeUnit.MILLISECONDS
                    )
                    .build()
                WorkManager.getInstance(ctx)
                    .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to enqueue RecoveryWorker", t)
            }
        }
    }
}
