package cl.coders.faketraveler.aether.network

import kotlinx.serialization.Serializable

/**
 * A single geographic waypoint (latitude / longitude pair).
 */
@Serializable
data class Waypoint(
    val lat: Double,
    val lon: Double,
)

/**
 * Request body for the route-alternatives endpoint.
 */
@Serializable
data class WaypointRequest(
    val waypoints: List<Waypoint>,
)
