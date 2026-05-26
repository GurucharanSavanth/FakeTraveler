package cl.coders.faketraveler.aether.mind

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * Movement modes the spoofing engine supports.
 * [resolveMode] maps (requested + detected real activity) to the actual mode used.
 */
enum class MovementMode {
    STILL,
    WALK,
    JOG,
    CYCLE,
    DRIVE,
    PASSENGER,
    UNKNOWN
}

/**
 * AetherMind: conflict-resolution engine for movement mode selection.
 *
 * Combines user-requested [MovementMode] with real-world activity recognition
 * from Play Services to produce a plausible spoofing mode. Optionally wraps a
 * TFLite model for future ML-enhanced resolution.
 *
 * ## Conflict rules
 * | Requested | Real activity   | Resolved    |
 * |-----------|-----------------|-------------|
 * | DRIVE     | WALKING/ON_FOOT | PASSENGER   |
 * | JOG       | IN_VEHICLE      | WALK        |
 * | CYCLE     | IN_VEHICLE      | PASSENGER   |
 *
 * All other combinations pass through the requested mode unchanged.
 *
 * @param tfLiteWrapper  optional TFLite inference wrapper (closed on [close])
 * @param arHelper       Play Services activity recognition helper
 * @param scope          coroutine scope used for activity collection; cancelled on [close]
 */
class AetherMind(
    private val tfLiteWrapper: TfLiteWrapper?,
    private val arHelper: ActivityRecognitionHelper,
    private val scope: CoroutineScope
) : Closeable {

    private val tag = "AetherMind"

    private val _realActivity = MutableStateFlow(DetectedActivityType.UNKNOWN)

    /** The most recently detected real-world activity. */
    val realActivity: StateFlow<DetectedActivityType> = _realActivity.asStateFlow()

    @Volatile
    private var collectionJob: Job? = null

    /**
     * Resolve the effective [MovementMode] given the user's [requested] mode
     * and the current real-world activity.
     *
     * Conflict rules:
     * - DRIVE + WALKING/ON_FOOT -> PASSENGER
     * - JOG + IN_VEHICLE -> WALK
     * - CYCLE + IN_VEHICLE -> PASSENGER
     */
    fun resolveMode(requested: MovementMode): Result<MovementMode> = runCatching {
        val real = _realActivity.value
        resolveConflict(requested, real)
    }

    /**
     * Resolve with an explicit real-activity override (useful for testing and
     * batch inference).
     */
    fun resolveMode(
        requested: MovementMode,
        realOverride: DetectedActivityType
    ): Result<MovementMode> = runCatching {
        resolveConflict(requested, realOverride)
    }

    private fun resolveConflict(
        requested: MovementMode,
        real: DetectedActivityType
    ): MovementMode {
        return when {
            // DRIVE + walking/on_foot -> user is a passenger, not driving
            requested == MovementMode.DRIVE &&
                (real == DetectedActivityType.WALKING || real == DetectedActivityType.ON_FOOT) ->
                MovementMode.PASSENGER

            // JOG + in vehicle -> downgrade to walk (can't jog in a car)
            requested == MovementMode.JOG &&
                real == DetectedActivityType.IN_VEHICLE ->
                MovementMode.WALK

            // CYCLE + in vehicle -> user is a passenger carrying a bike
            requested == MovementMode.CYCLE &&
                real == DetectedActivityType.IN_VEHICLE ->
                MovementMode.PASSENGER

            else -> requested
        }
    }

    /**
     * Run TFLite inference if a model is loaded. Returns the raw output floats,
     * or an empty array when no model is available.
     */
    fun infer(input: FloatArray): Result<FloatArray> {
        val wrapper = tfLiteWrapper
            ?: return Result.success(FloatArray(0))
        return wrapper.infer(input)
    }

    /**
     * Begin collecting activity recognition updates at 5-second intervals.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    fun startActivityUpdates(): Result<Unit> = runCatching {
        if (collectionJob?.isActive == true) return@runCatching

        arHelper.startActivityUpdates().getOrThrow()

        collectionJob = scope.launch {
            arHelper.activityFlow().collect { type ->
                _realActivity.value = type
                Log.d(tag, "Real activity updated: $type")
            }
        }
    }

    /**
     * Stop collecting activity recognition updates.
     */
    fun stopActivityUpdates(): Result<Unit> = runCatching {
        collectionJob?.cancel()
        collectionJob = null
        arHelper.stopActivityUpdates().getOrThrow()
    }

    override fun close() {
        collectionJob?.cancel()
        collectionJob = null
        try {
            arHelper.stopActivityUpdates()
        } catch (t: Throwable) {
            Log.w(tag, "stopActivityUpdates threw during close", t)
        }
        try {
            tfLiteWrapper?.close()
        } catch (t: Throwable) {
            Log.w(tag, "TfLiteWrapper.close() threw during close", t)
        }
    }
}
