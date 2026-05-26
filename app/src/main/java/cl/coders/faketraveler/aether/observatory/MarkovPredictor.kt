package cl.coders.faketraveler.aether.observatory

import androidx.datastore.core.DataStore
import cl.coders.faketraveler.aether.model.NoPredictionException
import cl.coders.faketraveler.aether.model.Prediction
import cl.coders.faketraveler.aether.model.PredictionEntry
import cl.coders.faketraveler.aether.model.TemporalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Markov chain predictor for app transitions.
 *
 * Maintains a first-order Markov chain where each state is a package name.
 * Transition probabilities are weighted by temporal similarity so that the
 * prediction favours patterns observed at similar times of day and on similar
 * days of the week.
 *
 * Chain state is persisted via Proto DataStore ([MarkovChain] schema defined
 * in W1-N2). Constructor-injected by Koin (W1-N1).
 */
class MarkovPredictor(
    private val dataStore: DataStore<MarkovChain>,
) {

    // ------------------------------------------------------------------
    // Prediction
    // ------------------------------------------------------------------

    /**
     * Predict the most likely next app given [currentApp] and a [context]
     * describing the current hour / day-of-week.
     *
     * @return [Result.success] with a [Prediction] containing the top
     *         candidate plus ranked alternatives, or [Result.failure] wrapping
     *         [NoPredictionException] when no transitions exist for [currentApp].
     */
    suspend fun predict(
        currentApp: String,
        context: TemporalContext,
    ): Result<Prediction> = withContext(Dispatchers.Default) {
        runCatching {
            val chain = dataStore.data.first()
            val transitions = chain.transitionsMap[currentApp]?.entriesList
                ?: throw NoPredictionException(currentApp)

            if (transitions.isEmpty()) {
                throw NoPredictionException(currentApp)
            }

            // Weight each transition by temporal similarity.
            val weighted = transitions.map { entry ->
                val similarity = temporalSimilarity(
                    entrySinHour = entry.sinHour,
                    entryCosHour = entry.cosHour,
                    entryDow = entry.dayOfWeek,
                    ctxSinHour = context.sinHour,
                    ctxCosHour = context.cosHour,
                    ctxDow = context.dayOfWeek,
                )
                entry.toApp to (entry.count * similarity)
            }

            val totalWeight = weighted.sumOf { it.second.toDouble() }.toFloat()
            if (totalWeight <= 0f) {
                throw NoPredictionException(currentApp)
            }

            // Normalise into probabilities and sort descending.
            val ranked = weighted
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, weights) -> weights.sum() / totalWeight }
                .entries
                .sortedByDescending { it.value }

            val top = ranked.first()
            val alternatives = ranked
                .drop(1)
                .take(ObservatoryConstants.MAX_ALTERNATIVES)
                .map { PredictionEntry(packageName = it.key, probability = it.value) }

            Prediction(
                nextApp = top.key,
                confidence = top.value,
                alternatives = alternatives,
            )
        }
    }

    // ------------------------------------------------------------------
    // Recording
    // ------------------------------------------------------------------

    /**
     * Record an observed transition from [from] to [to].
     *
     * Increments the transition count in the persisted Markov chain and
     * stores the current temporal snapshot so future predictions can
     * weight by time-of-day similarity.
     */
    suspend fun recordTransition(from: String, to: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                val now = currentTemporalContext()
                dataStore.updateData { chain ->
                    val builder = chain.toBuilder()

                    val bucket = builder.transitionsMap[from]?.toBuilder()
                        ?: TransitionBucket.newBuilder()

                    // Find existing entry for this (from -> to) pair or create new.
                    val existingIdx = bucket.entriesList.indexOfFirst { it.toApp == to }
                    if (existingIdx >= 0) {
                        val updated = bucket.getEntries(existingIdx).toBuilder()
                            .setCount(bucket.getEntries(existingIdx).count + 1)
                            .setSinHour(now.sinHour)
                            .setCosHour(now.cosHour)
                            .setDayOfWeek(now.dayOfWeek)
                            .build()
                        bucket.setEntries(existingIdx, updated)
                    } else {
                        bucket.addEntries(
                            TransitionEntry.newBuilder()
                                .setToApp(to)
                                .setCount(1)
                                .setSinHour(now.sinHour)
                                .setCosHour(now.cosHour)
                                .setDayOfWeek(now.dayOfWeek)
                                .build()
                        )
                    }

                    builder.putTransitions(from, bucket.build())
                    builder.build()
                }
            }
        }

    // ------------------------------------------------------------------
    // Temporal helpers
    // ------------------------------------------------------------------

    /**
     * Cosine-style similarity between two temporal snapshots.
     *
     * Hour similarity uses the unit-circle encoding (sin/cos of hour angle)
     * so that 23:55 and 00:05 are considered close.  Day-of-week match is
     * binary (1.0 for same day, 0.0 otherwise).
     *
     * Returns a value in [0, 1].
     */
    private fun temporalSimilarity(
        entrySinHour: Float,
        entryCosHour: Float,
        entryDow: Int,
        ctxSinHour: Float,
        ctxCosHour: Float,
        ctxDow: Int,
    ): Float {
        // Dot-product of two unit vectors gives cos(angle); map from [-1,1] to [0,1].
        val hourDot = (entrySinHour * ctxSinHour + entryCosHour * ctxCosHour)
        val hourSim = ((hourDot + 1f) / 2f).coerceIn(0f, 1f)

        val dowSim = if (entryDow == ctxDow) 1f else 0f

        return ObservatoryConstants.TEMPORAL_HOUR_WEIGHT * hourSim +
            ObservatoryConstants.TEMPORAL_DOW_WEIGHT * dowSim
    }

    /**
     * Build a [TemporalContext] for the current wall-clock time.
     *
     * Uses `java.time` (desugared to API 21+ via coreLibraryDesugaring) to
     * avoid kotlinx-datetime `Clock.System` dependency on the android-jvm target.
     * The hour is encoded on the unit circle so midnight wraps smoothly.
     */
    private fun currentTemporalContext(): TemporalContext {
        val now = java.time.LocalDateTime.now()
        val hourFraction = now.hour + now.minute / 60f
        val angle = (2.0 * Math.PI * hourFraction / 24.0)
        return TemporalContext(
            sinHour = sin(angle).toFloat(),
            cosHour = cos(angle).toFloat(),
            dayOfWeek = now.dayOfWeek.value, // 1 = Monday .. 7 = Sunday (ISO)
        )
    }
}
