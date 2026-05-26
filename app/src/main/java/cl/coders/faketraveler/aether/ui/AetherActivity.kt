package cl.coders.faketraveler.aether.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.coders.faketraveler.aether.ui.navigation.AetherNavHost
import cl.coders.faketraveler.aether.ui.theme.AetherTheme

/**
 * Main Compose entry point for the Aether Engine UI.
 * Edge-to-edge rendering with M3 dynamic color theme.
 */
class AetherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AetherTheme {
                AetherNavHost()
            }
        }
    }
}
