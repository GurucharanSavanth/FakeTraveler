package cl.coders.faketraveler.aether.ui.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Profile Editor screen with a map for pin-drop and a persistent bottom sheet for
 * profile configuration.
 *
 * Layout: TopAppBar (back + title) -> MapLibreView -> persistent BottomSheet containing:
 * name field, coordinates, MovementMode FilterChips, target apps LazyColumn with search,
 * accuracy jitter toggle, interval Slider, Save FAB, Delete button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorScreen(
    viewModel: ProfileEditorViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigate back after save/delete
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            viewModel.consumeSaved()
            onNavigateBack()
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    val title = if (state.profileId != null) "Edit Profile" else "New Profile"

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        sheetPeekHeight = 300.dp,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            ProfileSheetContent(
                state = state,
                onNameChange = viewModel::updateName,
                onMovementModeChange = viewModel::updateMovementMode,
                onToggleApp = viewModel::toggleApp,
                onAccuracyJitterChange = viewModel::updateAccuracyJitter,
                onIntervalChange = viewModel::updateIntervalMs,
                onSave = viewModel::save,
                onDelete = viewModel::delete
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                MapLibreView(
                    lat = state.lat,
                    lng = state.lng,
                    onLocationSelected = viewModel::updateLocation,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSheetContent(
    state: ProfileEditorUiState,
    onNameChange: (String) -> Unit,
    onMovementModeChange: (MovementMode) -> Unit,
    onToggleApp: (String) -> Unit,
    onAccuracyJitterChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    var appSearchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Drag handle indicator
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .padding(bottom = 0.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Profile Name
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text("Profile Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Coordinates display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Lat: ${"%.6f".format(state.lat)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Lng: ${"%.6f".format(state.lng)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Movement Mode
        Text(
            text = "Movement Mode",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MovementMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.movementMode == mode,
                    onClick = { onMovementModeChange(mode) },
                    label = { Text(mode.label) },
                    leadingIcon = if (state.movementMode == mode) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Target Apps
        Text(
            text = "Target Apps",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Search field
        OutlinedTextField(
            value = appSearchQuery,
            onValueChange = { appSearchQuery = it },
            label = { Text("Search apps") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // App list
        val filteredApps = remember(state.targetApps, appSearchQuery) {
            if (appSearchQuery.isBlank()) {
                state.targetApps
            } else {
                state.targetApps.filter {
                    it.appName.contains(appSearchQuery, ignoreCase = true) ||
                            it.packageName.contains(appSearchQuery, ignoreCase = true)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            items(
                items = filteredApps,
                key = { it.packageName }
            ) { app ->
                AppListItem(
                    app = app,
                    selected = app.packageName in state.selectedApps,
                    onToggle = { onToggleApp(app.packageName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Accuracy Jitter Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Accuracy Jitter",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Randomize accuracy to avoid detection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.accuracyJitter,
                onCheckedChange = onAccuracyJitterChange
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Update Interval Slider
        Text(
            text = "Update Interval: ${state.updateIntervalMs}ms",
            style = MaterialTheme.typography.bodyLarge
        )
        Slider(
            value = state.updateIntervalMs.toFloat(),
            onValueChange = { onIntervalChange(it.toInt()) },
            valueRange = 100f..5000f,
            steps = 48,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("100ms", style = MaterialTheme.typography.labelSmall)
            Text("5000ms", style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.profileId != null) {
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            ExtendedFloatingActionButton(
                onClick = onSave,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                },
                text = { Text("Save") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AppListItem(
    app: AppInfo,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
