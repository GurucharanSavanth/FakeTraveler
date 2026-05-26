package cl.coders.faketraveler.aether.forge.enrichment

import android.hardware.GeomagneticField
import android.location.Location
import android.os.Bundle

/**
 * Computes magnetic declination for a forged location and injects it
 * into the [Location.getExtras] bundle under key [EXTRAS_KEY].
 *
 * Wraps [android.hardware.GeomagneticField] (IGRF/WMM model) which is
 * available on all API levels supported by this app (minSdk 21).
 *
 * No constructor dependencies -- pure computation against the Android
 * public API.
 */
class MagneticDeclinationGuard {

    /**
     * Computes the magnetic declination at the given position and time.
     *
     * @param lat      Latitude in decimal degrees (WGS-84).
     * @param lon      Longitude in decimal degrees (WGS-84).
     * @param altitude Altitude in metres above WGS-84 ellipsoid.
     * @param timeMs   Timestamp in milliseconds since Unix epoch.
     * @return Declination in decimal degrees (east-positive).
     */
    fun compute(lat: Double, lon: Double, altitude: Double, timeMs: Long): Double {
        val field = GeomagneticField(
            lat.toFloat(),
            lon.toFloat(),
            altitude.toFloat(),
            timeMs,
        )
        return field.declination.toDouble()
    }

    /**
     * Convenience: computes declination and writes it into the [Location]'s
     * extras bundle under [EXTRAS_KEY]. Creates the bundle if absent.
     *
     * @return The computed declination value.
     */
    fun applyTo(location: Location, altitude: Double, timeMs: Long): Double {
        val declination = compute(
            lat = location.latitude,
            lon = location.longitude,
            altitude = altitude,
            timeMs = timeMs,
        )
        val extras = location.extras ?: Bundle()
        extras.putDouble(EXTRAS_KEY, declination)
        location.extras = extras
        return declination
    }

    companion object {
        /** Bundle key written into [Location.getExtras]. */
        const val EXTRAS_KEY = "magnetic_declination"
    }
}
