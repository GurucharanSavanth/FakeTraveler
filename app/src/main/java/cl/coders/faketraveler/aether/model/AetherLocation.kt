package cl.coders.faketraveler.aether.model

data class AetherLocation(
    val lat: Double,
    val lon: Double,
    val altitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val declination: Double,
    val satellites: Int,
    val provider: MockProvider
)
