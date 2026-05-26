package cl.coders.faketraveler.aether.observatory

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import cl.coders.faketraveler.aether.model.AppTransition
import cl.coders.faketraveler.aether.model.Prediction
import cl.coders.faketraveler.aether.model.TemporalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Observatory engine: detects foreground-app transitions and predicts the next
 * app the user is likely to open.
 *
 * **Primary source**: [UsageStatsManager] polled at [ObservatoryConstants.POLL_INTERVAL_MS].
 * **Fallback source**: [AetherAccessibilityService] via [AccessibilityManager] when
 * UsageStats permission is denied.
 *
 * Constructor params are Koin constructor-injected (W1-N1).
 *
 * @param usageStats        System [UsageStatsManager] service.
 * @param accessibilityManager System [AccessibilityManager] — used to check whether
 *                           the accessibility fallback is enabled.
 * @param packageManager    System [PackageManager] for validating package names.
 * @param markovPredictor   [MarkovPredictor] for Markov-chain predictions.
 * @param scope             Application-scoped [CoroutineScope] with a SupervisorJob.
 */
class ObservatoryEngine(
    private val usageStats: UsageStatsManager,
    private val accessibilityManager: AccessibilityManager,
    private val packageManager: PackageManager,
    private val markovPredictor: MarkovPredictor,
    private val scope: CoroutineScope,
) {

    // ------------------------------------------------------------------
    // App-event stream
    // ------------------------------------------------------------------

    /**
     * Hot [Flow] of foreground-app transitions.
     *
     * Behaviour:
     * - Polls [UsageStatsManager.queryEvents] every 400 ms.
     * - Filters for [UsageEvents.Event.MOVE_TO_FOREGROUND] only.
     * - Validates the package against installed apps.
     * - Uses a [Channel.CONFLATED] buffer so rapid switches drop stale events.
     * - [distinctUntilChanged] deduplicates consecutive identical packages.
     * - Runs on [Dispatchers.Default] to keep the main thread clear.
     */
    val appEvents: Flow<AppTransition> = callbackFlow {
        var lastTimestamp = System.currentTimeMillis()
        var previousPackage: String? = null

        val ticker: Timer = fixedRateTimer(
            name = "observatory-poll",
            daemon = true,
            initialDelay = 0L,
            period = ObservatoryConstants.POLL_INTERVAL_MS,
        ) {
            if (!isActive) return@fixedRateTimer

            val now = System.currentTimeMillis()
            val usageEvents: UsageEvents? = runCatching {
                usageStats.queryEvents(lastTimestamp, now)
            }.getOrNull()

            if (usageEvents != null) {
                val event = UsageEvents.Event()
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)

                    if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue

                    val pkg = event.packageName ?: continue
                    if (!isPackageInstalled(pkg)) continue

                    // Record the transition for the Markov chain.
                    val fromPkg = previousPackage
                    if (fromPkg != null && fromPkg != pkg) {
                        // Fire-and-forget; errors are swallowed intentionally.
                        scope.launch(Dispatchers.Default) {
                            markovPredictor.recordTransition(fromPkg, pkg)
                        }
                    }
                    previousPackage = pkg

                    val transition = AppTransition(
                        packageName = pkg,
                        timestamp = Instant.fromEpochMilliseconds(event.timeStamp),
                    )
                    trySend(transition)
                }
            }
            lastTimestamp = now
        }

        awaitClose { ticker.cancel() }
    }
        .buffer(Channel.CONFLATED)
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    /**
     * Fallback flow backed by [AetherAccessibilityService].
     *
     * Active only when UsageStats permission is denied and the accessibility
     * service has been enabled by the user.  Same conflation / dedup semantics
     * as [appEvents].
     */
    val accessibilityEvents: Flow<AppTransition> =
        AetherAccessibilityService.foregroundPackage
            .map { pkg ->
                AppTransition(
                    packageName = pkg,
                    timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                )
            }
            .buffer(Channel.CONFLATED)
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    // ------------------------------------------------------------------
    // Prediction
    // ------------------------------------------------------------------

    /**
     * Predict the next app the user will open after [current].
     *
     * The call is budgeted to [ObservatoryConstants.PREDICTION_BUDGET_MS]; if
     * the Markov predictor exceeds that window the result is a failure.
     *
     * @return [Result.success] with a [Prediction], or [Result.failure] with
     *         [NoPredictionException] / [kotlinx.coroutines.TimeoutCancellationException].
     */
    suspend fun predictNext(current: String): Result<Prediction> =
        withContext(Dispatchers.Default) {
            val context = currentTemporalContext()
            val prediction = withTimeoutOrNull(ObservatoryConstants.PREDICTION_BUDGET_MS) {
                markovPredictor.predict(current, context)
            }
            prediction ?: Result.failure(
                RuntimeException("Prediction timed out after ${ObservatoryConstants.PREDICTION_BUDGET_MS}ms")
            )
        }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Check whether [packageName] corresponds to an installed application.
     *
     * Suppresses [PackageManager.NameNotFoundException] and returns `false`
     * for unresolvable packages.
     */
    private fun isPackageInstalled(packageName: String): Boolean =
        runCatching {
            @Suppress("DEPRECATION") // getPackageInfo(String, int) is the only variant on API 21
            packageManager.getPackageInfo(packageName, 0)
        }.isSuccess

    /**
     * Snapshot the current temporal context for the Markov predictor.
     */
    private fun currentTemporalContext(): TemporalContext {
        val now = java.time.LocalDateTime.now()
        val hourFraction = now.hour + now.minute / 60f
        val angle = (2.0 * Math.PI * hourFraction / 24.0)
        return TemporalContext(
            sinHour = sin(angle).toFloat(),
            cosHour = cos(angle).toFloat(),
            dayOfWeek = now.dayOfWeek.value, // ISO: 1 = Mon .. 7 = Sun
        )
    }
}
