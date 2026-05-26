package cl.coders.faketraveler.aether.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Bounding box for spatial queries: (south, west, north, east).
 */
@Serializable
data class BoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
)

/**
 * A road segment returned from the Supabase road-segments endpoint.
 */
@Serializable
data class RoadSegment(
    val id: Long,
    val name: String? = null,
    @SerialName("max_speed") val maxSpeed: Int? = null,
    val geometry: GeoJsonGeometry,
)

/**
 * GeoJSON properties attached to a feature.
 */
@Serializable
data class GeoJsonProperties(
    @SerialName("maxspeed") val maxSpeed: Int? = null,
)

/**
 * GeoJSON geometry with type discriminator and coordinate arrays.
 *
 * Coordinates are polymorphic per RFC 7946:
 * - Point:      `[lon, lat]`              (flat array of numbers)
 * - LineString:  `[[lon, lat], ...]`       (array of positions)
 * - Polygon:    `[[[lon, lat], ...], ...]` (array of rings)
 *
 * We store the raw [JsonElement] to avoid deserialization failures on any
 * geometry type. Callers should use the typed accessor helpers below.
 */
@Serializable
data class GeoJsonGeometry(
    val type: String,
    val coordinates: JsonElement,
)

/**
 * A single GeoJSON feature within a feature collection.
 */
@Serializable
data class GeoJsonFeature(
    val type: String = "Feature",
    val properties: GeoJsonProperties? = null,
    val geometry: GeoJsonGeometry,
)

/**
 * A GeoJSON FeatureCollection representing a route.
 */
@Serializable
data class GeoJsonRoute(
    val type: String = "FeatureCollection",
    val features: List<GeoJsonFeature> = emptyList(),
)

/**
 * Single elevation lookup result from the Open-Elevation API.
 */
@Serializable
data class ElevationResult(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
)

/**
 * Top-level response wrapper from the Open-Elevation API.
 */
@Serializable
data class ElevationResponse(
    val results: List<ElevationResult>,
)
