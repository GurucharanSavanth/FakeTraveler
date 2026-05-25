package cl.coders.faketraveler

import android.app.Application
import com.google.android.material.color.DynamicColors
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import cl.coders.faketraveler.aether.di.appModule
import cl.coders.faketraveler.aether.di.aetherModule
import cl.coders.faketraveler.aether.di.dataModule
import cl.coders.faketraveler.aether.di.networkModule

class AetherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        initKoin()
    }

    private fun initKoin() {
        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@AetherApplication)
            modules(
                appModule,
                dataModule,
                networkModule,
                aetherModule
            )
        }
    }
}
