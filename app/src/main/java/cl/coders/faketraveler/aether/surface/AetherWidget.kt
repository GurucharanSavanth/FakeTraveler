package cl.coders.faketraveler.aether.surface

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import cl.coders.faketraveler.MockedLocationService

/**
 * Glance-based home screen widget for Fake Traveler.
 *
 * Displays the active profile name, mock status, and a toggle button.
 * Uses Glance Column/Row/Text/Button composition.
 */
class AetherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("FakeTravelerPrefs.v3", Context.MODE_PRIVATE)
        val isMocking = prefs.getBoolean("isMocking", false)
        val profileName = prefs.getString("activeProfile", "Default") ?: "Default"

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(GlanceTheme.colors.surface),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Header row
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fake Traveler",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.onSurface
                            )
                        )
                    }

                    // Profile name
                    Text(
                        text = profileName,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant
                        ),
                        modifier = GlanceModifier.padding(top = 4.dp)
                    )

                    // Status
                    Text(
                        text = if (isMocking) "Mocking Active" else "Inactive",
                        style = TextStyle(
                            fontWeight = FontWeight.Medium,
                            color = if (isMocking) {
                                GlanceTheme.colors.primary
                            } else {
                                GlanceTheme.colors.onSurfaceVariant
                            }
                        ),
                        modifier = GlanceModifier.padding(top = 8.dp)
                    )

                    // Toggle row
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (isMocking) "Stop" else "Start",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.primary
                            ),
                            modifier = GlanceModifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable(actionRunCallback<ToggleMockAction>())
                        )
                    }
                }
            }
        }
    }
}

/**
 * Callback action for widget toggle button.
 * Sends start/stop intent to MockedLocationService.
 */
class ToggleMockAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = context.getSharedPreferences("FakeTravelerPrefs.v3", Context.MODE_PRIVATE)
        val isMocking = prefs.getBoolean("isMocking", false)

        val intent = Intent(context, MockedLocationService::class.java).apply {
            action = if (isMocking) {
                MockedLocationService.ACTION_STOP
            } else {
                MockedLocationService.ACTION_RESUME
            }
        }

        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Throwable) {
            // Fallback for background start restrictions
            context.startService(intent)
        }

        // Update widget
        AetherWidget().update(context, glanceId)
    }
}
