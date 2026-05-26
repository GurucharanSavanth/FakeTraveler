package cl.coders.faketraveler.aether.surface

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * BroadcastReceiver that provides the [AetherWidget] instance to the Glance framework.
 * Declared in AndroidManifest.xml with the standard APPWIDGET_UPDATE intent filter.
 */
class AetherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AetherWidget()
}
