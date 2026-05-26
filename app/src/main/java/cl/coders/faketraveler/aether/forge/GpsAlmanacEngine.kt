package cl.coders.faketraveler.aether.forge

/**
 * Estimates the number of GPS satellites visible from a geographic position.
 *
 * Concrete implementation will be delivered in W2-N8 (enrichment layer).
 * This interface exists so [ForgeEngine] can be compiled and tested
 * with a stub/mock before the real almanac engine is available.
 */
fun interface GpsAlmanacEngine {

    /**
     * Estimate the number of GPS satellites visible at the given location and time.
     *
     * @param lat        Latitude in degrees.
     * @param lon        Longitude in degrees.
     * @param timeMillis Epoch time in milliseconds.
     * @return Estimated number of visible satellites (typically 4-14).
     */
    suspend fun visibleSatellites(lat: Double, lon: Double, timeMillis: Long): Int
}
