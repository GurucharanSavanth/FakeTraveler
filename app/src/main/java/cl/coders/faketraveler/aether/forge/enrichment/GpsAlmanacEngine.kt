package cl.coders.faketraveler.aether.forge.enrichment

import android.location.Location
import android.os.Bundle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simplified GPS almanac engine that produces deterministic, realistic
 * visible-satellite counts for forged locations.
 *
 * The GPS constellation has 31 operational satellites (PRN 1-31, with 32
 * reserved). At any point on Earth, typically 6-12 are above the 5-degree
 * elevation mask. This engine models that distribution using a deterministic
 * hash of (lat, lon, time) so that:
 *
 *  - Identical inputs always produce the same count.
 *  - Counts fall in the realistic 6-12 range.
 *  - Counts vary smoothly with position and time.
 *  - Polar regions see slightly fewer satellites.
 *
 * A future iteration can replace this with a proper Keplerian propagator
 * fed by live almanac data from the [httpClient].
 */
class GpsAlmanacEngine {

    /**
     * Returns the number of GPS satellites visible at the given location
     * and time.
     *
     * @param lat    Latitude in decimal degrees (WGS-84).
     * @param lon    Longitude in decimal degrees (WGS-84).
     * @param timeMs Timestamp in milliseconds since Unix epoch.
     * @return Visible satellite count (guaranteed 6..12 inclusive).
     */
    fun visibleSatellites(lat: Double, lon: Double, timeMs: Long): Int {
        // Each PRN is independently tested for visibility using a
        // deterministic pseudo-position derived from orbital parameters.
        var visible = 0
        for (prn in 1..CONSTELLATION_SIZE) {
            if (isSatelliteVisible(prn, lat, lon, timeMs)) {
                visible++
            }
        }
        // Clamp to realistic bounds -- even the model can under/overshoot
        // at extreme coordinates.
        return visible.coerceIn(MIN_VISIBLE, MAX_VISIBLE)
    }

    /**
     * Convenience: computes satellite count and writes it into the
     * [Location]'s extras bundle under [EXTRAS_KEY].
     *
     * @return The computed satellite count.
     */
    fun applyTo(location: Location, timeMs: Long): Int {
        val count = visibleSatellites(location.latitude, location.longitude, timeMs)
        val extras = location.extras ?: Bundle()
        extras.putInt(EXTRAS_KEY, count)
        location.extras = extras
        return count
    }

    // ---- simplified orbital model ----------------------------------------

    /**
     * Tests whether a single PRN is above the elevation mask at the
     * observer position and time.
     *
     * The "orbit" is modelled as a sinusoidal track whose phase depends
     * on the PRN and whose ground-track shifts with time. This is *not*
     * physically accurate, but produces the correct statistical
     * distribution.
     */
    private fun isSatelliteVisible(
        prn: Int,
        lat: Double,
        lon: Double,
        timeMs: Long,
    ): Boolean {
        // Orbital period ~11h 58m for GPS; discretise into 5-minute slots
        // so the constellation "rotates" with time.
        val timeSlot = timeMs / TIME_SLOT_MS

        // Deterministic pseudo-position for this PRN at this time slot.
        // Each PRN occupies one of 6 orbital planes separated by 60 deg.
        val plane = (prn - 1) % ORBITAL_PLANES
        val slot = (prn - 1) / ORBITAL_PLANES

        // Sub-satellite latitude: inclined orbit oscillates +/- 55 deg
        val phaseRad = ((plane * 60.0 + slot * 30.0 + timeSlot * ROTATION_STEP) % 360.0) *
            DEG_TO_RAD
        val satLat = INCLINATION * sin(phaseRad)

        // Sub-satellite longitude: drifts with time
        val satLon = ((plane * 60.0 + slot * 45.0 + timeSlot * ROTATION_STEP * 1.3) % 360.0) - 180.0

        // Angular distance from observer to sub-satellite point
        val angularDist = greatCircleDeg(lat, lon, satLat, satLon)

        // A satellite at ~20,200 km altitude is visible from the surface
        // up to ~76 degrees angular distance. Apply 5-degree mask.
        return angularDist < MAX_ANGULAR_DIST
    }

    /**
     * Approximate great-circle distance in degrees between two points.
     * Uses the equirectangular approximation -- sufficient for the
     * coarse visibility check.
     */
    private fun greatCircleDeg(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val dLat = (lat2 - lat1) * DEG_TO_RAD
        val dLon = (lon2 - lon1) * DEG_TO_RAD
        val avgLat = (lat1 + lat2) / 2.0 * DEG_TO_RAD
        val x = dLon * cos(avgLat)
        val y = dLat
        // Convert back to degrees
        return Math.toDegrees(kotlin.math.sqrt(x * x + y * y))
    }

    companion object {
        /** Bundle key written into [Location.getExtras]. */
        const val EXTRAS_KEY = "satellites"

        /** GPS constellation: PRNs 1-32 (31 active + 1 spare). */
        private const val CONSTELLATION_SIZE = 32

        /** Six orbital planes, evenly spaced at 60 degrees. */
        private const val ORBITAL_PLANES = 6

        /** GPS orbit inclination: 55 degrees. */
        private const val INCLINATION = 55.0

        /** Elevation mask: ignore satellites below 5 degrees. */
        @Suppress("unused")
        private const val ELEVATION_MASK_DEG = 5.0

        /**
         * Maximum angular distance from sub-satellite point at which the
         * satellite clears the 5-degree mask (~76 deg from geometry, but
         * tightened to 70 to produce realistic counts).
         */
        private const val MAX_ANGULAR_DIST = 70.0

        /** Time slot width in ms (5 minutes). */
        private const val TIME_SLOT_MS = 5L * 60L * 1000L

        /** Degrees the constellation rotates per time slot. */
        private const val ROTATION_STEP = 0.5

        private const val DEG_TO_RAD = PI / 180.0

        /** Realistic bounds for visible satellite count. */
        private const val MIN_VISIBLE = 6
        private const val MAX_VISIBLE = 12
    }
}
