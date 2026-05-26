package cl.coders.faketraveler.aether.sync

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

/**
 * Verifies atomic multi-provider injection via [TemporalSyncEngine].
 *
 * Uses Robolectric to shadow [LocationManager] so no real device is needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class TemporalSyncEngineTest {

    private lateinit var locationManager: LocationManager
    private lateinit var shadowLm: ShadowLocationManager
    private lateinit var injector: MultiProviderInjector
    private lateinit var quantumDispatcher: QuantumDispatcher
    private lateinit var engine: TemporalSyncEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowLm = Shadows.shadowOf(locationManager)

        injector = MultiProviderInjector(locationManager)
        injector.setupAll().getOrThrow()

        quantumDispatcher = QuantumDispatcher()
        engine = TemporalSyncEngine(injector, quantumDispatcher)
    }

    @After
    fun tearDown() {
        engine.close()
        injector.teardownAll()
    }

    @Test
    fun `all three providers receive identical elapsedRealtimeNanos`() = runTest {
        val result = engine.injectLocation(
            latitude = 37.7749,
            longitude = -122.4194,
        )
        assertTrue("Injection should succeed", result.isSuccess)

        val gps = lastLocation(LocationManager.GPS_PROVIDER)
        val network = lastLocation(LocationManager.NETWORK_PROVIDER)
        val passive = lastLocation(LocationManager.PASSIVE_PROVIDER)

        assertNotNull("GPS location should be set", gps)
        assertNotNull("NETWORK location should be set", network)
        assertNotNull("PASSIVE location should be set", passive)

        // All three must share the exact same elapsedRealtimeNanos snapshot.
        assertEquals(
            "GPS and NETWORK elapsedRealtimeNanos must match",
            checkNotNull(gps).elapsedRealtimeNanos,
            checkNotNull(network).elapsedRealtimeNanos,
        )
        assertEquals(
            "GPS and PASSIVE elapsedRealtimeNanos must match",
            gps.elapsedRealtimeNanos,
            checkNotNull(passive).elapsedRealtimeNanos,
        )
    }

    @Test
    fun `GPS accuracy is less than NETWORK accuracy`() = runTest {
        val baseAccuracy = 5.0f
        val result = engine.injectLocation(
            latitude = 48.8566,
            longitude = 2.3522,
            accuracy = baseAccuracy,
        )
        assertTrue("Injection should succeed", result.isSuccess)

        val gpsAccuracy = checkNotNull(lastLocation(LocationManager.GPS_PROVIDER)).accuracy
        val networkAccuracy = checkNotNull(lastLocation(LocationManager.NETWORK_PROVIDER)).accuracy

        assertTrue(
            "GPS accuracy ($gpsAccuracy) must be less than NETWORK accuracy ($networkAccuracy)",
            gpsAccuracy < networkAccuracy,
        )
        // GPS uses multiplier 1.0, NETWORK uses 1.5.
        assertEquals("GPS accuracy should equal base", baseAccuracy, gpsAccuracy, 0.001f)
        assertEquals(
            "NETWORK accuracy should be 1.5x base",
            baseAccuracy * 1.5f,
            networkAccuracy,
            0.001f,
        )
    }

    @Test
    fun `extras contain satellites and magnetic_declination`() = runTest {
        val result = engine.injectLocation(
            latitude = 35.6762,
            longitude = 139.6503,
        )
        assertTrue("Injection should succeed", result.isSuccess)

        for (providerName in PROVIDER_NAMES) {
            val loc = checkNotNull(lastLocation(providerName)) {
                "Location should be set for $providerName"
            }
            val extras = checkNotNull(loc.extras) {
                "Extras bundle should not be null for $providerName"
            }
            assertTrue(
                "$providerName extras must contain '${TemporalSyncEngine.EXTRA_SATELLITES}'",
                extras.containsKey(TemporalSyncEngine.EXTRA_SATELLITES),
            )
            assertTrue(
                "$providerName extras must contain '${TemporalSyncEngine.EXTRA_MAGNETIC_DECLINATION}'",
                extras.containsKey(TemporalSyncEngine.EXTRA_MAGNETIC_DECLINATION),
            )
        }

        // GPS should have non-zero satellites; NETWORK/PASSIVE should have 0.
        val gpsSatellites = checkNotNull(lastLocation(LocationManager.GPS_PROVIDER))
            .extras?.getInt(TemporalSyncEngine.EXTRA_SATELLITES) ?: -1
        assertEquals(
            "GPS should report default satellite count",
            TemporalSyncEngine.DEFAULT_SATELLITE_COUNT,
            gpsSatellites,
        )

        val networkSatellites = checkNotNull(lastLocation(LocationManager.NETWORK_PROVIDER))
            .extras?.getInt(TemporalSyncEngine.EXTRA_SATELLITES) ?: -1
        assertEquals(
            "NETWORK should report 0 satellites",
            0,
            networkSatellites,
        )
    }

    @Test
    fun `all providers receive correct coordinates`() = runTest {
        val lat = 51.5074
        val lon = -0.1278
        val alt = 42.0
        val result = engine.injectLocation(
            latitude = lat,
            longitude = lon,
            altitude = alt,
        )
        assertTrue("Injection should succeed", result.isSuccess)

        for (providerName in PROVIDER_NAMES) {
            val loc = checkNotNull(lastLocation(providerName)) {
                "Location should be set for $providerName"
            }
            assertEquals("$providerName latitude", lat, loc.latitude, 0.0001)
            assertEquals("$providerName longitude", lon, loc.longitude, 0.0001)
            assertEquals("$providerName altitude", alt, loc.altitude, 0.0001)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun lastLocation(provider: String): Location? =
        shadowLm.getLastKnownLocation(provider)

    companion object {
        private val PROVIDER_NAMES = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    }
}
