package cl.coders.faketraveler.aether.sync

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * Atomically injects mock locations into GPS, NETWORK, and PASSIVE providers
 * with identical timestamps.
 *
 * Design invariants:
 *  - A single [SystemClock.elapsedRealtimeNanos] snapshot is shared across all
 *    three providers so skew stays well under the 50 us budget.
 *  - All [Location] objects are pre-built **before** the [Mutex] is acquired to
 *    minimize lock hold time.
 *  - Injection runs on a dedicated [QuantumDispatcher] handler thread for
 *    deterministic scheduling.
 *
 * EXPECTED: awaits W1-N1 (AetherCoordinate), W1-N3 (FieldState) for full
 * integration. Currently operates on raw lat/lon doubles.
 */
class TemporalSyncEngine(
    private val injector: MultiProviderInjector,
    private val quantumDispatcher: QuantumDispatcher,
) : Closeable {

    private val mutex = Mutex()
    @Volatile
    private var closed = false

    /**
     * Atomically inject a location into all three providers.
     *
     * @param latitude  decimal degrees
     * @param longitude decimal degrees
     * @param altitude  metres above WGS-84 ellipsoid (default 0.0)
     * @param accuracy  horizontal accuracy in metres for GPS (default 3.0).
     *                  NETWORK receives accuracy * 1.5 per SYNC CONTRACT.
     * @return [Result.success] on success, [Result.failure] if any injection fails.
     */
    suspend fun injectLocation(
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        accuracy: Float = DEFAULT_ACCURACY,
    ): Result<Unit> {
        if (closed) {
            return Result.failure(IllegalStateException("TemporalSyncEngine is closed"))
        }

        // ── 1. Snapshot timestamps BEFORE building locations ─────────
        val wallTime = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()

        // ── 2. Pre-build all Location objects (no lock held) ─────────
        val locations = buildLocations(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy,
            wallTime = wallTime,
            elapsedNanos = elapsedNanos,
        )

        // ── 3. Acquire lock and inject on the dedicated thread ───────
        return withContext(quantumDispatcher.dispatcher) {
            mutex.withLock {
                runCatching {
                    locations.forEach { (providerName, location) ->
                        injector.inject(providerName, location).getOrThrow()
                    }
                }
            }
        }.also { result ->
            result.onFailure { e ->
                Log.e(TAG, "Atomic injection failed", e)
            }
        }
    }

    override fun close() {
        closed = true
        injector.teardownAll()
        quantumDispatcher.close()
    }

    // ── Private helpers ──────────────────────────────────────────────

    /**
     * Build a [Location] for every [MultiProviderInjector.ProviderSpec] using
     * the same timestamp pair. Returns a list of (providerName, Location).
     */
    private fun buildLocations(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        accuracy: Float,
        wallTime: Long,
        elapsedNanos: Long,
    ): List<Pair<String, Location>> =
        MultiProviderInjector.ProviderSpec.entries.map { spec ->
            val loc = Location(spec.providerName).apply {
                this.latitude = latitude
                this.longitude = longitude
                this.altitude = altitude
                time = wallTime
                elapsedRealtimeNanos = elapsedNanos
                speed = 0.01f
                bearing = 0f
                this.accuracy = accuracy * spec.accuracyMultiplier

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bearingAccuracyDegrees = 0.1f
                    verticalAccuracyMeters = 0.1f
                    speedAccuracyMetersPerSecond = 0.01f
                }

                @Suppress("NewApi") // Guarded below
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    elapsedRealtimeUncertaintyNanos = ELAPSED_REALTIME_UNCERTAINTY_NANOS
                }

                extras = buildExtras(spec)
            }
            spec.providerName to loc
        }

    /**
     * Build a [Bundle] of provider-specific extras.
     *
     * GPS providers carry satellite count; all providers carry magnetic
     * declination for downstream consumers.
     */
    private fun buildExtras(spec: MultiProviderInjector.ProviderSpec): Bundle =
        Bundle().apply {
            putInt(EXTRA_SATELLITES, if (spec == MultiProviderInjector.ProviderSpec.GPS) DEFAULT_SATELLITE_COUNT else 0)
            putFloat(EXTRA_MAGNETIC_DECLINATION, DEFAULT_MAGNETIC_DECLINATION)
        }

    companion object {
        private const val TAG = "TemporalSyncEngine"

        /** Default horizontal accuracy for GPS in metres. */
        const val DEFAULT_ACCURACY: Float = 3.0f

        /** API 34+ elapsed-realtime uncertainty in nanoseconds. */
        const val ELAPSED_REALTIME_UNCERTAINTY_NANOS: Double = 50_000.0

        /** Bundle key for satellite count extra. */
        const val EXTRA_SATELLITES: String = "satellites"

        /** Bundle key for magnetic declination extra (degrees). */
        const val EXTRA_MAGNETIC_DECLINATION: String = "magnetic_declination"

        /** Default satellite count injected for GPS provider. */
        const val DEFAULT_SATELLITE_COUNT: Int = 12

        /** Default magnetic declination in degrees. */
        const val DEFAULT_MAGNETIC_DECLINATION: Float = 0.0f
    }
}
