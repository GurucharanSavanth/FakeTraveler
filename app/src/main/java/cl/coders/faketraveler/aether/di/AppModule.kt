package cl.coders.faketraveler.aether.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module
import cl.coders.faketraveler.aether.data.db.AetherDatabase

val appModule = module {
    single(named("applicationScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single { AetherDatabase.create(get()) }
}
