package cl.coders.faketraveler.aether.ui.dashboard

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.coders.faketraveler.MockState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dashboard — the primary Aether Engine screen.
 *
 * Shows: TopAppBar "Aether", active profile card, MapLibre preview via AndroidView,
 * service status chips, kill-resistance L1-L7 chips, and a FAB to toggle mocking.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToObservatory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val fabScale by animateFloatAsState(
        targetValue = if (state.isMocking) 1.1f else 1.0f,
        animationSpec = tween(durationMillis = 300),
        label = "fab_scale"
    )

    val fabColor by animateColorAsState(
        targetValue = if (state.isMocking) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 300),
        label = "fab_color"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Aether",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.toggleMocking() },
                modifier = Modifier.scale(fabScale),
                containerColor = fabColor,
                contentColor = if (state.isMocking) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                icon = {
                    Icon(
                        imageVector = if (state.isMocking) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (state.isMocking) "Stop mocking" else "Start mocking"
                    )
                },
                text = {
                    Text(if (state.isMocking) "Stop" else "Start")
                }
            )
        },
        floatingActionButtonPosition = FabPosition.End
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Active Profile Card
            ActiveProfileCard(
                profileName = state.activeProfileName,
                lat = state.activeProfileLat,
                lng = state.activeProfileLng,
                mode = state.activeProfileMode,
                onClick = onNavigateToProfile
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Map Preview
            MapPreviewCard(
                lat = state.activeProfileLat,
                lng = state.activeProfileLng
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Service Status
            ServiceStatusSection(state = state)

            Spacer(modifier = Modifier.height(16.dp))

            // Kill Resistance Layers
            KillResistanceSection(layers = state.persistenceLayers)

            // Bottom spacing for FAB
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
private fun ActiveProfileCard(
    profileName: String?,
    lat: Double,
    lng: Double,
    mode: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profileName ?: "No profile selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profileName != null) {
                    Text(
                        text = "%.6f, %.6f".format(lat, lng),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = mode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * MapLibre preview rendered via AndroidView wrapping a WebView with a static OSM tile.
 * Falls back to a coordinate text placeholder when the map cannot load.
 */
@Composable
private fun MapPreviewCard(lat: Double, lng: Double) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        val zoom = 13
        val tileUrl = "https://tile.openstreetmap.org/$zoom/" +
                "${lonToTileX(lng, zoom)}/${latToTileY(lat, zoom)}.png"

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    loadDataWithBaseURL(
                        null,
                        buildMapHtml(lat, lng, tileUrl),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Dispose handled by Compose lifecycle — WebView is automatically destroyed
        // when the AndroidView leaves composition.
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceStatusSection(state: DashboardUiState) {
    Column {
        Text(
            text = "Service Status",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StatusChip(
                label = when (state.serviceState) {
                    MockState.MOCKED -> "Active"
                    MockState.SERVICE_BOUND -> "Bound"
                    MockState.MOCK_ERROR -> "Error"
                    MockState.NOT_MOCKED -> "Inactive"
                },
                active = state.serviceState == MockState.MOCKED
            )

            if (state.lastInjectionTime > 0L) {
                val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                StatusChip(
                    label = "Last: ${timeFormat.format(Date(state.lastInjectionTime))}",
                    active = true
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KillResistanceSection(layers: List<PersistenceLayerStatus>) {
    Column {
        Text(
            text = "Kill Resistance",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            layers.forEach { layer ->
                FilterChip(
                    selected = layer.active,
                    onClick = { /* read-only display */ },
                    label = {
                        Text(
                            text = "L${layer.layer} ${layer.name}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                    )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

// --- Map tile helpers ---

private fun lonToTileX(lon: Double, zoom: Int): Int {
    return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
}

private fun latToTileY(lat: Double, zoom: Int): Int {
    val latRad = Math.toRadians(lat)
    return ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
}

private fun buildMapHtml(lat: Double, lng: Double, tileUrl: String): String {
    return """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
            body { margin: 0; padding: 0; overflow: hidden; background: #1a1a2e; }
            .container { position: relative; width: 100%; height: 100%; display: flex;
                         align-items: center; justify-content: center; }
            img { width: 100%; height: 100%; object-fit: cover; opacity: 0.85; }
            .pin { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -100%);
                   font-size: 32px; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.4)); }
            .coords { position: absolute; bottom: 8px; left: 8px; background: rgba(0,0,0,0.6);
                      color: white; padding: 4px 8px; border-radius: 4px; font-size: 11px;
                      font-family: monospace; }
        </style>
        </head><body>
        <div class="container">
            <img src="$tileUrl" alt="Map" onerror="this.style.display='none'"/>
            <div class="pin">📍</div>
            <div class="coords">${"%.4f".format(lat)}, ${"%.4f".format(lng)}</div>
        </div>
        </body></html>
    """.trimIndent()
}
