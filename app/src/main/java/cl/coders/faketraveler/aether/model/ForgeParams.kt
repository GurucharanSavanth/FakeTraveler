package cl.coders.faketraveler.aether.model

data class ForgeParams(
    val base: GeoPoint,
    val movementMode: MovementMode,
    val speedMps: Double,
    val bearingDeg: Double,
    val timeSeconds: Double,
    val provider: MockProvider
)
