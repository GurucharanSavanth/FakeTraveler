package cl.coders.faketraveler.aether.di

import org.koin.dsl.module
import cl.coders.faketraveler.aether.data.db.AetherDatabase

val dataModule = module {
    single { get<AetherDatabase>().profileDao() }
    single { get<AetherDatabase>().elevationCacheDao() }
}
