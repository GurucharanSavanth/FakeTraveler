package cl.coders.faketraveler.aether.model

data class ElevationResult(
    val elevation: Double,
    val source: ElevationSource
)

enum class ElevationSource {
    CACHE,
    NETWORK,
    DEFAULT
}
