package cl.coders.faketraveler.aether.sync

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.util.Log

/**
 * Manages setup, injection, and teardown of mock test providers for
 * GPS, NETWORK, and PASSIVE [LocationManager] providers.
 *
 * API 31+ uses [ProviderProperties.Builder]; API <31 uses the legacy
 * boolean-arg overload of [LocationManager.addTestProvider].
 */
open class MultiProviderInjector(private val locationManager: LocationManager) {

    /**
     * Provider configurations with per-provider properties.
     *
     * Raw int constants mirror ProviderProperties.ACCURACY_* / POWER_USAGE_*
     * values (API 31+) but are declared inline to avoid a class-load crash on
     * API 21-30 where [ProviderProperties] or its constants may not exist.
     */
    enum class ProviderSpec(
        val providerName: String,
        val requiresSatellite: Boolean,
        val requiresNetwork: Boolean,
        val accuracyType: Int,
        val powerUsage: Int,
        val accuracyMultiplier: Float,
    ) {
        GPS(
            providerName = LocationManager.GPS_PROVIDER,
            requiresSatellite = true,
            requiresNetwork = false,
            accuracyType = ACCURACY_FINE,
            powerUsage = POWER_USAGE_HIGH,
            accuracyMultiplier = 1.0f,
        ),
        NETWORK(
            providerName = LocationManager.NETWORK_PROVIDER,
            requiresSatellite = false,
            requiresNetwork = true,
            accuracyType = ACCURACY_COARSE,
            powerUsage = POWER_USAGE_LOW,
            accuracyMultiplier = 1.5f,
        ),
        PASSIVE(
            providerName = LocationManager.PASSIVE_PROVIDER,
            requiresSatellite = false,
            requiresNetwork = false,
            accuracyType = ACCURACY_COARSE,
            powerUsage = POWER_USAGE_LOW,
            accuracyMultiplier = 1.0f,
        ),
    }

    private val activeProviders = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Register and enable all three test providers.
     *
     * @return [Result.success] with the list of provider names set up, or
     *         [Result.failure] if any provider could not be added.
     */
    fun setupAll(): Result<List<String>> = runCatching {
        ProviderSpec.entries.map { spec ->
            setupProvider(spec)
            spec.providerName
        }
    }

    /**
     * Inject a pre-built [Location] into the given provider.
     *
     * @return [Result.success] on success, [Result.failure] on security or
     *         illegal-argument errors.
     */
    open fun inject(providerName: String, location: Location): Result<Unit> = runCatching {
        locationManager.setTestProviderLocation(providerName, location)
    }

    /**
     * Tear down all registered test providers. Safe to call multiple times.
     */
    fun teardownAll() {
        activeProviders.toList().forEach { name ->
            try {
                locationManager.setTestProviderEnabled(name, false)
            } catch (t: Throwable) {
                Log.w(TAG, "disable provider $name failed", t)
            }
            try {
                locationManager.removeTestProvider(name)
            } catch (t: Throwable) {
                Log.w(TAG, "remove provider $name failed", t)
            }
            activeProviders.remove(name)
        }
    }

    // ── Internal ─────────────────────────────────────────────────────

    private fun setupProvider(spec: ProviderSpec) {
        val name = spec.providerName

        // Defensive: remove any leftover provider before re-adding.
        try {
            locationManager.removeTestProvider(name)
        } catch (_: Throwable) {
            // May not exist yet; safe to ignore.
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addTestProviderModern(spec)
        } else {
            addTestProviderLegacy(spec)
        }

        locationManager.setTestProviderEnabled(name, true)
        activeProviders.add(name)
    }

    @SuppressLint("NewApi") // Guarded by SDK_INT >= S
    private fun addTestProviderModern(spec: ProviderSpec) {
        val props = ProviderProperties.Builder()
            .setHasAltitudeSupport(true)
            .setHasSpeedSupport(true)
            .setHasBearingSupport(true)
            .setPowerUsage(spec.powerUsage)
            .setAccuracy(spec.accuracyType)
            .build()
        locationManager.addTestProvider(spec.providerName, props)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("WrongConstant")
    private fun addTestProviderLegacy(spec: ProviderSpec) {
        // Map inline constants to legacy Criteria int values for API <31.
        val legacyPower = when (spec.powerUsage) {
            POWER_USAGE_HIGH -> 3
            POWER_USAGE_MEDIUM -> 2
            else -> 1 // LOW
        }
        val legacyAccuracy = when (spec.accuracyType) {
            ACCURACY_FINE -> 1
            else -> 2 // COARSE
        }
        locationManager.addTestProvider(
            /* provider            */ spec.providerName,
            /* requiresNetwork     */ spec.requiresNetwork,
            /* requiresSatellite   */ spec.requiresSatellite,
            /* requiresCell        */ false,
            /* hasMonetaryCost     */ false,
            /* supportsAltitude    */ true,
            /* supportsSpeed       */ true,
            /* supportsBearing     */ true,
            /* powerRequirement    */ legacyPower,
            /* accuracy            */ legacyAccuracy,
        )
    }

    companion object {
        private const val TAG = "MultiProviderInjector"

        // Mirrors of ProviderProperties constants (API 31+), declared inline so
        // the enum class can be loaded safely on API 21-30.
        const val ACCURACY_FINE = 1   // ProviderProperties.ACCURACY_FINE
        const val ACCURACY_COARSE = 2 // ProviderProperties.ACCURACY_COARSE
        const val POWER_USAGE_LOW = 1    // ProviderProperties.POWER_USAGE_LOW
        const val POWER_USAGE_MEDIUM = 2 // ProviderProperties.POWER_USAGE_MEDIUM
        const val POWER_USAGE_HIGH = 3   // ProviderProperties.POWER_USAGE_HIGH
    }
}
