package cl.coders.faketraveler.aether.observatory

import androidx.datastore.core.DataStore
import cl.coders.faketraveler.aether.data.proto.MarkovChain
import cl.coders.faketraveler.aether.model.NoPredictionException
import cl.coders.faketraveler.aether.model.Prediction
import cl.coders.faketraveler.aether.model.PredictionEntry
import cl.coders.faketraveler.aether.model.TemporalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * Markov chain predictor for app transitions.
 *
 * Persists transition probabilities in the W1-N2 [MarkovChain] proto schema.
 */
class MarkovPredictor(
    private val dataStore: DataStore<MarkovChain>,
) {

    suspend fun predict(
        currentApp: String,
        context: TemporalContext,
    ): Result<Prediction> = withContext(Dispatchers.Default) {
        runCatching {
            val chain = dataStore.data.first()
            val transitions = chain.transitionsList.filter { it.fromApp == currentApp }
            if (transitions.isEmpty()) {
                throw NoPredictionException(currentApp)
            }

            val weighted = transitions.map { entry ->
                val probability = if (entry.probability > 0f) entry.probability else 1f
                entry.toApp to probability * temporalSimilarity(
                    entryHour = entry.hour,
                    entryDow = entry.dayOfWeek,
                    context = context,
                )
            }

            val totalWeight = weighted.sumOf { it.second.toDouble() }.toFloat()
            if (totalWeight <= 0f) {
                throw NoPredictionException(currentApp)
            }

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

    suspend fun recordTransition(from: String, to: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                val now = java.time.LocalDateTime.now()
                dataStore.updateData { chain ->
                    val transitions = chain.transitionsList.toMutableList()
                    val existingIndex = transitions.indexOfFirst { it.fromApp == from && it.toApp == to }

                    if (existingIndex >= 0) {
                        val existing = transitions[existingIndex]
                        transitions[existingIndex] = existing.toBuilder()
                            .setProbability(existing.probability + 1f)
                            .setHour(now.hour)
                            .setDayOfWeek(now.dayOfWeek.value)
                            .build()
                    } else {
                        transitions += MarkovChain.Transition.newBuilder()
                            .setFromApp(from)
                            .setToApp(to)
                            .setProbability(1f)
                            .setHour(now.hour)
                            .setDayOfWeek(now.dayOfWeek.value)
                            .build()
                    }

                    val total = transitions
                        .filter { it.fromApp == from }
                        .sumOf { it.probability.toDouble() }
                        .toFloat()
                        .coerceAtLeast(1f)

                    val normalized = transitions.map { transition ->
                        if (transition.fromApp == from) {
                            transition.toBuilder()
                                .setProbability(transition.probability / total)
                                .build()
                        } else {
                            transition
                        }
                    }

                    MarkovChain.newBuilder()
                        .addAllTransitions(normalized)
                        .build()
                }
                Unit
            }
        }

    private fun temporalSimilarity(
        entryHour: Int,
        entryDow: Int,
        context: TemporalContext,
    ): Float {
        val hour = entryHour.coerceIn(0, 23)
        val angle = (2.0 * Math.PI * hour / 24.0)
        val entrySinHour = sin(angle).toFloat()
        val entryCosHour = cos(angle).toFloat()
        val hourDot = entrySinHour * context.sinHour + entryCosHour * context.cosHour
        val hourSim = ((hourDot + 1f) / 2f).coerceIn(0f, 1f)
        val dowSim = if (entryDow == context.dayOfWeek) 1f else 0f

        return ObservatoryConstants.TEMPORAL_HOUR_WEIGHT * hourSim +
            ObservatoryConstants.TEMPORAL_DOW_WEIGHT * dowSim
    }
}
