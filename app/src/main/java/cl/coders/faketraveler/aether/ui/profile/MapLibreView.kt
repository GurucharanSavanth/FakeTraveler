package cl.coders.faketraveler.aether.ui.profile

import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * MapLibre-style map view for the profile editor.
 *
 * Renders an interactive OSM-based map via AndroidView wrapping a WebView with Leaflet.js.
 * Long-press on the map drops a pin and invokes [onLocationSelected].
 *
 * Falls back to editable coordinate TextFields when the WebView fails to load
 * (e.g. no internet, WebView unavailable).
 *
 * Lifecycle: DisposableEffect handles WebView cleanup on dispose.
 */
@Composable
fun MapLibreView(
    lat: Double,
    lng: Double,
    onLocationSelected: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (!mapError) {
            // WebView-based map with Leaflet
            var webViewRef by remember { mutableStateOf<WebView?>(null) }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)

                        addJavascriptInterface(
                            MapBridge { newLat, newLng ->
                                onLocationSelected(newLat, newLng)
                            },
                            "MapBridge"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                mapLoaded = true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                mapError = true
                            }
                        }

                        loadDataWithBaseURL(
                            "https://unpkg.com/",
                            buildLeafletMapHtml(lat, lng),
                            "text/html",
                            "UTF-8",
                            null
                        )
                        webViewRef = this
                    }
                },
                update = { webView ->
                    // Update marker position when coordinates change externally
                    webView.evaluateJavascript(
                        "if(typeof updateMarker === 'function') updateMarker($lat, $lng);",
                        null
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )

            // DisposableEffect for cleanup
            DisposableEffect(Unit) {
                onDispose {
                    webViewRef?.destroy()
                    webViewRef = null
                }
            }
        }

        if (mapError || !mapLoaded) {
            // Fallback coordinate TextFields
            CoordinateFallbackFields(
                lat = lat,
                lng = lng,
                onLocationSelected = onLocationSelected
            )
        }
    }
}

/**
 * Fallback editable coordinate fields shown when the map cannot load.
 */
@Composable
private fun CoordinateFallbackFields(
    lat: Double,
    lng: Double,
    onLocationSelected: (lat: Double, lng: Double) -> Unit
) {
    var latText by remember(lat) { mutableStateOf(if (lat == 0.0) "" else "%.6f".format(lat)) }
    var lngText by remember(lng) { mutableStateOf(if (lng == 0.0) "" else "%.6f".format(lng)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Enter coordinates manually",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = latText,
                onValueChange = { value ->
                    latText = value
                    val parsed = value.toDoubleOrNull()
                    if (parsed != null && parsed in -90.0..90.0) {
                        onLocationSelected(parsed, lngText.toDoubleOrNull() ?: lng)
                    }
                },
                label = { Text("Latitude") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = lngText,
                onValueChange = { value ->
                    lngText = value
                    val parsed = value.toDoubleOrNull()
                    if (parsed != null && parsed in -180.0..180.0) {
                        onLocationSelected(latText.toDoubleOrNull() ?: lat, parsed)
                    }
                },
                label = { Text("Longitude") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * JavaScript interface bridging map long-press events back to Compose.
 * Called from the Leaflet contextmenu (long-press) handler.
 */
private class MapBridge(
    private val onLongPress: (lat: Double, lng: Double) -> Unit
) {
    @JavascriptInterface
    fun onMapLongPress(lat: Double, lng: Double) {
        onLongPress(lat, lng)
    }
}

/**
 * Builds the Leaflet.js HTML for the interactive map.
 * Long-press (contextmenu) drops a marker and calls back via MapBridge.
 */
private fun buildLeafletMapHtml(lat: Double, lng: Double): String {
    return """
    <!DOCTYPE html>
    <html><head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
        html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; }
    </style>
    </head><body>
    <div id="map"></div>
    <script>
        var map = L.map('map', { zoomControl: false }).setView([$lat, $lng], 13);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; OSM contributors',
            maxZoom: 19
        }).addTo(map);

        var marker = L.marker([$lat, $lng]).addTo(map);

        function updateMarker(lat, lng) {
            marker.setLatLng([lat, lng]);
            map.setView([lat, lng], map.getZoom());
        }

        // Long-press via contextmenu event (mobile long-press maps to contextmenu)
        map.on('contextmenu', function(e) {
            var lat = e.latlng.lat;
            var lng = e.latlng.lng;
            marker.setLatLng([lat, lng]);
            if (window.MapBridge) {
                MapBridge.onMapLongPress(lat, lng);
            }
        });

        // Also support regular click for easier desktop testing
        map.on('click', function(e) {
            var lat = e.latlng.lat;
            var lng = e.latlng.lng;
            marker.setLatLng([lat, lng]);
            if (window.MapBridge) {
                MapBridge.onMapLongPress(lat, lng);
            }
        });
    </script>
    </body></html>
    """.trimIndent()
}
