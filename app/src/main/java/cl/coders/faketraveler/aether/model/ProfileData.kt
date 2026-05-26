package cl.coders.faketraveler.aether.model

import kotlinx.datetime.Instant

data class ProfileData(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val altitude: Double?,
    val movementMode: MovementMode,
    val targetApps: List<String>,
    val accuracyJitter: Boolean,
    val updateIntervalMs: Long,
    val createdAt: Instant
)
