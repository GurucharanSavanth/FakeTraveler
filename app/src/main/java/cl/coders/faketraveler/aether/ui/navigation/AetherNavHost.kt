package cl.coders.faketraveler.aether.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Top-level navigation host for the Aether Compose UI.
 * Four routes with placeholder composables; real screens land in later nodes.
 */
@Composable
fun AetherNavHost(
    navController: NavHostController = rememberNavController()
) {
    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.DASHBOARD) {
                PlaceholderScreen(title = "Dashboard")
            }
            composable(Routes.PROFILE_EDITOR) {
                PlaceholderScreen(title = "Profile Editor")
            }
            composable(Routes.OBSERVATORY) {
                PlaceholderScreen(title = "Observatory")
            }
            composable(Routes.SETTINGS) {
                PlaceholderScreen(title = "Settings")
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
