package cl.coders.faketraveler.aether.shroud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import cl.coders.faketraveler.MainActivity
import cl.coders.faketraveler.R
import java.util.Locale

/**
 * Notification factory for the Aether foreground service.
 *
 * Two channels:
 * - [MOCK_CHANNEL_ID] ("aether_mock"): IMPORTANCE_LOW, ongoing mock status.
 * - [HEALTH_CHANNEL_ID] ("aether_health"): IMPORTANCE_HIGH, health/error alerts.
 *
 * All PendingIntents use FLAG_IMMUTABLE on API 23+ per platform requirement.
 */
object AetherNotificationFactory {

    // ── Channel IDs ──
    const val MOCK_CHANNEL_ID = "aether_mock"
    const val HEALTH_CHANNEL_ID = "aether_health"

    // ── Notification IDs ──
    const val NOTIFICATION_ID = 2025
    const val HEALTH_NOTIFICATION_ID = 2026

    // ── Action constants ──
    private const val REQUEST_CODE_STOP = 100
    private const val REQUEST_CODE_SWITCH = 101
    private const val REQUEST_CODE_OPEN = 102
    private const val REQUEST_CODE_HEALTH = 103

    // ── PendingIntent flags ──
    private val PI_FLAGS: Int
        get() = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    // ------------------------------------------------------------------
    // Channel creation
    // ------------------------------------------------------------------

    /**
     * Create the low-importance mock channel on API 26+. Idempotent.
     */
    fun ensureMockChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(MOCK_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            MOCK_CHANNEL_ID,
            context.getString(R.string.Notification_ChannelName),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.Notification_ChannelDesc)
            setShowBadge(false)
            enableVibration(false)
        }
        runCatching { nm.createNotificationChannel(channel) }
    }

    /**
     * Create the high-importance health channel on API 26+. Idempotent.
     */
    fun ensureHealthChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(HEALTH_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            HEALTH_CHANNEL_ID,
            context.getString(R.string.Health_ChannelName),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.Health_ChannelDesc)
        }
        runCatching { nm.createNotificationChannel(channel) }
    }

    // ------------------------------------------------------------------
    // Ongoing mock notification
    // ------------------------------------------------------------------

    /**
     * Build the ongoing foreground notification for active mocking.
     *
     * Includes three actions:
     * - STOP: broadcast to [AetherService.ACTION_STOP]
     * - SWITCH_PROFILE: deep-link to profile picker
     * - OPEN_APP: launch [MainActivity]
     *
     * @param context     Application context.
     * @param profileName Name of the active profile.
     * @param lat         Current mocked latitude.
     * @param lon         Current mocked longitude.
     */
    fun buildOngoing(
        context: Context,
        profileName: String,
        lat: Double,
        lon: Double,
    ): Notification {
        ensureMockChannel(context)

        val stopPi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_STOP,
            Intent(AetherService.ACTION_STOP).setPackage(context.packageName),
            PI_FLAGS,
        )

        val switchPi = PendingIntent.getActivity(
            context,
            REQUEST_CODE_SWITCH,
            Intent(AetherService.ACTION_SWITCH_PROFILE).apply {
                setPackage(context.packageName)
                setClass(context, MainActivity::class.java)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PI_FLAGS,
        )

        val openPi = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PI_FLAGS,
        )

        val body = String.format(
            Locale.ROOT,
            "%s: %.6f, %.6f",
            profileName,
            lat,
            lon,
        )

        return NotificationCompat.Builder(context, MOCK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mock_notification)
            .setContentTitle(context.getString(R.string.Notification_Title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(openPi)
            .addAction(0, context.getString(R.string.Notification_Stop), stopPi)
            .addAction(0, "Switch", switchPi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    // ------------------------------------------------------------------
    // Error notification
    // ------------------------------------------------------------------

    /**
     * Build a dismissible error notification on the mock channel.
     *
     * @param context Application context.
     * @param message Human-readable error description.
     */
    fun buildError(context: Context, message: String): Notification {
        ensureMockChannel(context)

        val openPi = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PI_FLAGS,
        )

        return NotificationCompat.Builder(context, MOCK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mock_notification)
            .setContentTitle(context.getString(R.string.Notification_ErrorTitle))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(false)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPi)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
    }

    // ------------------------------------------------------------------
    // Health failure notification
    // ------------------------------------------------------------------

    /**
     * Build a high-importance health-failure notification on the health channel.
     *
     * Tapping opens Developer Settings so the user can re-enable mock providers.
     *
     * @param context Application context.
     * @param reason  Human-readable failure reason.
     */
    fun buildHealthFailure(context: Context, reason: String): Notification {
        ensureHealthChannel(context)

        val devSettingsPi = PendingIntent.getActivity(
            context,
            REQUEST_CODE_HEALTH,
            Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PI_FLAGS,
        )

        return NotificationCompat.Builder(context, HEALTH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mock_notification)
            .setContentTitle(context.getString(R.string.Health_FailedTitle))
            .setContentText(context.getString(R.string.Health_FailedMsg, reason))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(devSettingsPi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
    }
}
