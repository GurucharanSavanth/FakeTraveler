package cl.coders.faketraveler.aether.forge

/**
 * Computes magnetic declination for a geographic coordinate.
 *
 * Concrete implementation will be delivered in W2-N8 (enrichment layer).
 * This interface exists so [ForgeEngine] can be compiled and tested
 * with a stub/mock before the real guard is available.
 */
fun interface MagneticDeclinationGuard {

    /**
     * Compute magnetic declination in degrees for the given position.
     *
     * Positive values indicate east declination; negative indicate west.
     *
     * @param lat        Latitude in degrees.
     * @param lon        Longitude in degrees.
     * @param altMeters  Altitude in meters above mean sea level.
     * @param timeMillis Epoch time in milliseconds (declination varies over time).
     * @return Declination in degrees.
     */
    suspend fun compute(lat: Double, lon: Double, altMeters: Double, timeMillis: Long): Double
}
