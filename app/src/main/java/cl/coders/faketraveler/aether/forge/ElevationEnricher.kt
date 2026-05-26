package cl.coders.faketraveler.aether.forge

/**
 * Provides terrain elevation for a geographic coordinate.
 *
 * Concrete implementation will be delivered in W2-N8 (enrichment layer).
 * This interface exists so [ForgeEngine] can be compiled and tested
 * with a stub/mock before the real enricher is available.
 */
fun interface ElevationEnricher {

    /**
     * Return elevation in meters above mean sea level for the given coordinate.
     *
     * @param lat  Latitude in degrees.
     * @param lon  Longitude in degrees.
     * @return Elevation in meters. Implementations that cannot resolve the
     *         coordinate should return a sensible default (e.g. 0.0).
     */
    suspend fun get(lat: Double, lon: Double): Double
}
