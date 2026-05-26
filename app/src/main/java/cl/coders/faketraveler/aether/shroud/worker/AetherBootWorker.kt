package cl.coders.faketraveler.aether.shroud.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cl.coders.faketraveler.MockedLocationService
import cl.coders.faketraveler.PermissionChecker
import cl.coders.faketraveler.SharedPrefsUtil

/**
 * Aether v2 boot worker. Runs after [AetherBootReceiver] enqueues it with a 10 s
 * delay. Checks that the user still wants restore-after-boot and that mock-location
 * permission is intact before starting [MockedLocationService] with ACTION_RESUME.
 *
 * No `!!`, no `GlobalScope`, no `runBlocking`.
 */
class AetherBootWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            if (!SharedPrefsUtil.isRestoreAfterBoot(ctx)) {
                Log.d(TAG, "restoreAfterBoot is off; nothing to resume")
                return Result.success()
            }

            if (!PermissionChecker.isMockLocationEnabled(ctx)) {
                Log.w(TAG, "Mock location permission not granted; cannot resume")
                return Result.success()
            }

            val svc = Intent(ctx, MockedLocationService::class.java).apply {
                action = MockedLocationService.ACTION_RESUME
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(ctx, svc)
            } else {
                ctx.startService(svc)
            }

            Log.i(TAG, "Started MockedLocationService with ACTION_RESUME")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Boot resume failed; will retry", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AetherBootWorker"
    }
}
