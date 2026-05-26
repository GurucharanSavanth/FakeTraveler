package cl.coders.faketraveler.aether.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * Thin Ktor wrapper around the [Open-Elevation API](https://api.open-elevation.com).
 *
 * Uses the shared [HttpClient] (CIO engine, content-negotiation + JSON already
 * installed) so connection pooling and timeouts are consistent app-wide.
 *
 * @param client Shared [HttpClient] instance provided via constructor injection.
 */
class ElevationApi(
    private val client: HttpClient,
) {

    /**
     * Look up the elevation (meters above sea level) for a single coordinate.
     *
     * @param lat Latitude in decimal degrees.
     * @param lon Longitude in decimal degrees.
     * @return    Elevation in meters, wrapped in [Result].
     */
    suspend fun lookup(lat: Double, lon: Double): Result<Double> =
        try {
            val response: ElevationResponse = client.get(OPEN_ELEVATION_URL) {
                parameter("locations", "$lat,$lon")
            }.body()

            val results = response.results
            check(results.isNotEmpty()) {
                "Open-Elevation returned empty results for ($lat, $lon)"
            }
            Result.success(results.first().elevation)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }

    private companion object {
        const val OPEN_ELEVATION_URL = "https://api.open-elevation.com/api/v1/lookup"
    }
}
