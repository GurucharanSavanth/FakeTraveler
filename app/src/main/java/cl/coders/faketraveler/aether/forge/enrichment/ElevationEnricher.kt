package cl.coders.faketraveler.aether.forge.enrichment

import cl.coders.faketraveler.aether.db.ElevationCacheDao
import cl.coders.faketraveler.aether.db.ElevationCacheEntity
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.floor
import kotlin.time.Duration.Companion.days

/**
 * Enriches forged locations with real-world elevation data.
 *
 * Resolution: ~111 m grid (lat/lon truncated to 3 decimal places).
 * Source: Open-Elevation public API.
 * Cache: Room [ElevationCacheDao] with 90-day TTL.
 *
 * Network failures fall back to cached data (even if stale) or 0.0.
 */
class ElevationEnricher(
    private val elevationCacheDao: ElevationCacheDao,
    private val httpClient: HttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns elevation in metres for the given coordinate.
     *
     * Lookup order:
     * 1. Fresh cache hit (within [TTL]).
     * 2. Remote fetch from Open-Elevation API, then cache.
     * 3. Stale cache hit (any age).
     * 4. Default [DEFAULT_ELEVATION].
     */
    suspend fun get(lat: Double, lon: Double): Result<Double> = runCatching {
        val latIdx = floor(lat * GRID_SCALE).toInt()
        val lonIdx = floor(lon * GRID_SCALE).toInt()
        val now = Clock.System.now()

        // 1. Try fresh cache
        val cached = elevationCacheDao.get(latIdx, lonIdx)
        if (cached != null && isFresh(cached.fetchedAt, now)) {
            return@runCatching cached.elevation
        }

        // 2. Remote fetch
        val remote = fetchRemote(lat, lon)
        if (remote != null) {
            val entity = ElevationCacheEntity(
                latIdx = latIdx,
                lonIdx = lonIdx,
                elevation = remote,
                fetchedAt = now,
            )
            elevationCacheDao.upsert(entity)
            return@runCatching remote
        }

        // 3. Stale cache fallback
        if (cached != null) {
            return@runCatching cached.elevation
        }

        // 4. Default
        DEFAULT_ELEVATION
    }

    /**
     * Evicts entries older than [TTL]. Call periodically (e.g. from WorkManager).
     */
    suspend fun evictStale(): Result<Unit> = runCatching {
        val cutoff = Clock.System.now() - TTL
        elevationCacheDao.evictOlderThan(cutoff)
    }

    // ---- internal --------------------------------------------------------

    private fun isFresh(fetchedAt: Instant, now: Instant): Boolean =
        (now - fetchedAt) < TTL

    /**
     * Queries the Open-Elevation REST API. Returns `null` on any failure
     * so the caller can fall through to cache or default.
     */
    private suspend fun fetchRemote(lat: Double, lon: Double): Double? =
        try {
            val response = httpClient.get(API_URL) {
                parameter("locations", "$lat,$lon")
            }
            val body = response.bodyAsText()
            val parsed = json.decodeFromString<OpenElevationResponse>(body)
            parsed.results.firstOrNull()?.elevation
        } catch (_: Exception) {
            // Network/parse failure -- caller falls back
            null
        }

    // ---- models -----------------------------------------------------------

    @Serializable
    private data class OpenElevationResponse(
        val results: List<ElevationResult> = emptyList(),
    )

    @Serializable
    private data class ElevationResult(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val elevation: Double = 0.0,
    )

    companion object {
        /** Cache grid resolution: 1/1000 degree (~111 m). */
        private const val GRID_SCALE = 1_000.0

        /** Time-to-live for cached elevation values. */
        private val TTL = 90.days

        /** Fallback elevation when all sources fail. */
        private const val DEFAULT_ELEVATION = 0.0

        private const val API_URL =
            "https://api.open-elevation.com/api/v1/lookup"
    }
}
