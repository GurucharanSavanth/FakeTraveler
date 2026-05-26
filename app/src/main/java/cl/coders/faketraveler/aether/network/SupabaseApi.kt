package cl.coders.faketraveler.aether.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * Typed Ktor wrapper around the Supabase REST API endpoints used by FakeTraveler.
 *
 * All public methods are suspend functions that return [Result] so callers never
 * have to catch transport or parsing exceptions.
 *
 * @param client   Shared [HttpClient] with CIO engine, content-negotiation, and
 *                 JSON serialization already installed.
 * @param baseUrl  Supabase project URL, e.g. `https://<ref>.supabase.co`.
 * @param anonKey  Supabase anonymous (publishable) API key.
 */
class SupabaseApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val anonKey: String,
) {

    // ---- Elevation tiles ----

    /**
     * Fetch a pre-computed elevation tile from the Supabase storage bucket.
     *
     * @return Raw PNG/protobuf bytes of the tile at zoom [z], column [x], row [y].
     */
    suspend fun fetchElevationTile(z: Int, x: Int, y: Int): Result<ByteArray> =
        runCatchingApi {
            val response = client.get("$baseUrl/storage/v1/object/public/elevation-tiles/$z/$x/$y") {
                header(HEADER_API_KEY, anonKey)
            }
            response.requireSuccess("fetchElevationTile")
            response.body<ByteArray>()
        }

    // ---- Road segments ----

    /**
     * Query road segments that fall within the given [bbox].
     */
    suspend fun fetchRoadSegments(bbox: BoundingBox): Result<List<RoadSegment>> =
        runCatchingApi {
            val response = client.get("$baseUrl/rest/v1/rpc/road_segments_in_bbox") {
                header(HEADER_API_KEY, anonKey)
                parameter("south", bbox.south)
                parameter("west", bbox.west)
                parameter("north", bbox.north)
                parameter("east", bbox.east)
            }
            response.requireSuccess("fetchRoadSegments")
            response.body<List<RoadSegment>>()
        }

    // ---- Route alternatives ----

    /**
     * Request a route alternative through the given [waypoints].
     */
    suspend fun fetchRouteAlternative(
        waypoints: List<Waypoint>,
    ): Result<GeoJsonRoute> =
        runCatchingApi {
            val response = client.post("$baseUrl/rest/v1/rpc/route_alternative") {
                header(HEADER_API_KEY, anonKey)
                contentType(ContentType.Application.Json)
                setBody(WaypointRequest(waypoints))
            }
            response.requireSuccess("fetchRouteAlternative")
            response.body<GeoJsonRoute>()
        }

    // ---- Profile persistence ----

    /**
     * Save a JSON-encoded profile blob for the authenticated user.
     *
     * @param jwt  Bearer token obtained via Supabase Auth.
     * @param blob Raw JSON string to store.
     */
    suspend fun saveProfile(jwt: String, blob: String): Result<Unit> =
        runCatchingApi {
            val response = client.post("$baseUrl/rest/v1/rpc/save_profile") {
                header(HEADER_API_KEY, anonKey)
                header(HEADER_AUTHORIZATION, "Bearer $jwt")
                // Send raw JSON string without double-encoding through
                // content-negotiation's serializer.
                setBody(TextContent(blob, ContentType.Application.Json))
            }
            response.requireSuccess("saveProfile")
        }

    /**
     * Load the stored profile blob for the authenticated user.
     *
     * @param jwt Bearer token obtained via Supabase Auth.
     * @return    The JSON string, or `null` if no profile exists yet.
     */
    suspend fun loadProfile(jwt: String): Result<String?> =
        runCatchingApi {
            val response = client.get("$baseUrl/rest/v1/rpc/load_profile") {
                header(HEADER_API_KEY, anonKey)
                header(HEADER_AUTHORIZATION, "Bearer $jwt")
            }
            response.requireSuccess("loadProfile")
            val text = response.bodyAsText().trim()
            // PostgREST returns the JSON literal "null" for SQL NULL values.
            if (text.isBlank() || text == "null") null else text
        }

    // ---- Internals ----

    /**
     * Wraps a suspend lambda in try/catch, returning [Result] so callers never
     * deal with raw exceptions. Re-throws [CancellationException] to preserve
     * structured concurrency.
     */
    private inline fun <T> runCatchingApi(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }

    /**
     * Throws [IllegalStateException] with a clear message when the response
     * status is not 2xx, preventing misleading deserialization errors.
     */
    private suspend fun HttpResponse.requireSuccess(operation: String) {
        if (!status.isSuccess()) {
            val body = bodyAsText().take(MAX_ERROR_BODY_LENGTH)
            error("$operation failed: HTTP $status — $body")
        }
    }

    private companion object {
        const val HEADER_API_KEY = "apikey"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val MAX_ERROR_BODY_LENGTH = 512
    }
}
