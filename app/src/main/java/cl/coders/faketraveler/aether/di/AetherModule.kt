package cl.coders.faketraveler.aether.di

// import org.koin.core.qualifier.named  // uncomment when Observatory binding is enabled
import org.koin.dsl.module

// Engine bindings -- actual classes will be created by other agents (W2 nodes).
// This module defines the wiring; implementations must exist before Koin resolves.
val aetherModule = module {
    // Observatory
    // single { ObservatoryEngine(get(), get(), get(), get(named("applicationScope"))) }

    // Forge
    // single { FbmJitterEngine(hurst = 0.75f, amplitudeMeters = 8.0f) }
    // single { ElevationEnricher(get(), get()) }
    // single { MagneticDeclinationGuard() }
    // single { GpsAlmanacEngine(get()) }
    // single { ForgeEngine(get(), get(), get(), get()) }

    // Sync
    // single { TemporalSyncEngine(get(), get()) }

    // Mind
    // single { AetherMind(get(), get()) }
}
// NOTE: Commented out until engine classes exist. Each W2 agent should uncomment their binding.
