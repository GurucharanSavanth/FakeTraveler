package cl.coders.faketraveler.aether.mind

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Detected activity types surfaced by [ActivityRecognitionHelper].
 * Mapped from Play Services [DetectedActivity] type constants.
 */
enum class DetectedActivityType {
    IN_VEHICLE,
    ON_BICYCLE,
    ON_FOOT,
    RUNNING,
    STILL,
    TILTING,
    WALKING,
    UNKNOWN;

    companion object {
        fun fromDetectedActivity(type: Int): DetectedActivityType = when (type) {
            DetectedActivity.IN_VEHICLE -> IN_VEHICLE
            DetectedActivity.ON_BICYCLE -> ON_BICYCLE
            DetectedActivity.ON_FOOT -> ON_FOOT
            DetectedActivity.RUNNING -> RUNNING
            DetectedActivity.STILL -> STILL
            DetectedActivity.TILTING -> TILTING
            DetectedActivity.WALKING -> WALKING
            else -> UNKNOWN
        }
    }
}

/**
 * Wraps [ActivityRecognitionClient] from Google Play Services.
 *
 * Registers a [PendingIntent] at a 5-second interval to receive activity
 * recognition updates. Handles missing Play Services gracefully by returning
 * empty flows / successful no-ops instead of throwing.
 */
class ActivityRecognitionHelper(private val context: Context) {

    private val tag = "ActivityRecognitionHelper"
    private val actionActivityRecognition =
        "cl.coders.faketraveler.action.ACTIVITY_RECOGNITION"
    private val updateIntervalMs = 5_000L

    /**
     * Signature-level permission used on pre-Tiramisu to prevent other apps
     * from injecting fake activity recognition broadcasts.
     * Mirrors the pattern used by [MockedLocationService.StopReceiver].
     */
    private val receiverPermission =
        "cl.coders.faketraveler.permission.STOP_MOCK"

    private var client: ActivityRecognitionClient? = null
    private var pendingIntent: PendingIntent? = null

    /** True when Play Services is available on this device. */
    val isPlayServicesAvailable: Boolean
        get() {
            val code = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context)
            return code == ConnectionResult.SUCCESS
        }

    /**
     * Start activity recognition updates at 5-second intervals.
     * Returns [Result.success] even when Play Services is absent (graceful no-op).
     */
    @Suppress("MissingPermission")
    fun startActivityUpdates(): Result<Unit> = runCatching {
        if (!isPlayServicesAvailable) {
            Log.w(tag, "Play Services unavailable; activity recognition disabled")
            return@runCatching
        }
        val arClient = ActivityRecognition.getClient(context)
        client = arClient

        val intent = Intent(actionActivityRecognition).apply {
            setPackage(context.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pi = PendingIntent.getBroadcast(context, 0, intent, flags)
        pendingIntent = pi

        arClient.requestActivityUpdates(updateIntervalMs, pi)
            .addOnFailureListener { e ->
                Log.e(tag, "Failed to request activity updates", e)
            }
    }

    /**
     * Stop activity recognition updates. Safe to call even if never started.
     */
    @Suppress("MissingPermission")
    fun stopActivityUpdates(): Result<Unit> = runCatching {
        val pi = pendingIntent ?: return@runCatching
        client?.removeActivityUpdates(pi)
            ?.addOnFailureListener { e ->
                Log.w(tag, "Failed to remove activity updates", e)
            }
        pendingIntent = null
        client = null
    }

    /**
     * Returns a cold [Flow] of [DetectedActivityType] that emits on each
     * activity recognition broadcast. The flow registers a [BroadcastReceiver]
     * on collect and unregisters on cancellation.
     *
     * When Play Services is unavailable the flow never emits (but does not error).
     */
    fun activityFlow(): Flow<DetectedActivityType> = callbackFlow {
        if (!isPlayServicesAvailable) {
            Log.w(tag, "Play Services unavailable; activityFlow will not emit")
            awaitClose()
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                if (intent == null) return
                if (ActivityRecognitionResult.hasResult(intent)) {
                    val result = ActivityRecognitionResult.extractResult(intent)
                        ?: return
                    val mostProbable = result.mostProbableActivity
                    val type = DetectedActivityType.fromDetectedActivity(
                        mostProbable.type
                    )
                    trySend(type)
                }
            }
        }

        val filter = IntentFilter(actionActivityRecognition)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver, filter, Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(receiver, filter, receiverPermission, null)
        }

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (t: Throwable) {
                Log.w(tag, "unregisterReceiver threw", t)
            }
        }
    }

    /**
     * Suspend-friendly one-shot: request updates, await a single recognition
     * result, then stop. Returns [DetectedActivityType.UNKNOWN] on timeout or
     * Play Services absence.
     */
    suspend fun detectOnce(): Result<DetectedActivityType> = runCatching {
        if (!isPlayServicesAvailable) {
            return@runCatching DetectedActivityType.UNKNOWN
        }
        suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent?) {
                    if (intent == null) return
                    if (ActivityRecognitionResult.hasResult(intent)) {
                        val result = ActivityRecognitionResult.extractResult(intent)
                        val type = if (result != null) {
                            DetectedActivityType.fromDetectedActivity(
                                result.mostProbableActivity.type
                            )
                        } else {
                            DetectedActivityType.UNKNOWN
                        }
                        try {
                            context.unregisterReceiver(this)
                        } catch (_: Throwable) { }
                        if (cont.isActive) cont.resume(type)
                    }
                }
            }

            val filter = IntentFilter(actionActivityRecognition)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver, filter, Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(receiver, filter, receiverPermission, null)
            }

            cont.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Throwable) { }
            }
        }
    }
}
