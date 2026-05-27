package cl.coders.faketraveler.aether.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.coders.faketraveler.MockState
import cl.coders.faketraveler.SettingsDataStore
import cl.coders.faketraveler.db.AppDatabase
import cl.coders.faketraveler.db.BookmarkDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Persistence layer status for the kill-resistance chips shown on the dashboard.
 * Each layer maps to one of the Aether Engine's L1-L7 survival mechanisms.
 */
data class PersistenceLayerStatus(
    val layer: Int,
    val name: String,
    val active: Boolean
)

/**
 * Immutable UI state snapshot for the dashboard screen.
 *
 * [serviceState] — current mock service lifecycle state.
 * [activeProfileName] — display name of the currently selected profile (null = none).
 * [activeProfileLat]/[activeProfileLng] — coordinates of the active profile.
 * [activeProfileMode] — movement mode label (e.g. "Static", "Drift", "Route").
 * [persistenceLayers] — L1-L7 kill-resistance layer statuses.
 * [lastInjectionTime] — epoch millis of the most recent location push, 0 if never.
 */
data class DashboardUiState(
    val serviceState: MockState = MockState.NOT_MOCKED,
    val activeProfileName: String? = null,
    val activeProfileLat: Double = 0.0,
    val activeProfileLng: Double = 0.0,
    val activeProfileMode: String = "Static",
    val persistenceLayers: List<PersistenceLayerStatus> = defaultLayers(),
    val lastInjectionTime: Long = 0L
) {
    val isMocking: Boolean get() = serviceState == MockState.MOCKED

    companion object {
        fun defaultLayers(): List<PersistenceLayerStatus> = listOf(
            PersistenceLayerStatus(1, "Foreground Service", false),
            PersistenceLayerStatus(2, "WorkManager Recovery", false),
            PersistenceLayerStatus(3, "Boot Receiver", false),
            PersistenceLayerStatus(4, "Health Monitor", false),
            PersistenceLayerStatus(5, "OEM Exemption", false),
            PersistenceLayerStatus(6, "Sticky Notification", false),
            PersistenceLayerStatus(7, "Auto-Restart", false)
        )
    }
}

/**
 * ViewModel for the Dashboard screen. Reads the active profile from [BookmarkDao]
 * and exposes a single [StateFlow] of [DashboardUiState].
 *
 * Constructor injection via Koin: receives [BookmarkDao] directly.
 */
class DashboardViewModel(
    application: Application,
    private val bookmarkDao: BookmarkDao
) : AndroidViewModel(application) {

    private val settingsStore = SettingsDataStore.get(application)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadActiveProfile()
        loadPersistenceLayers()
    }

    private fun loadActiveProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            val bookmarks = bookmarkDao.allSync
            val active = bookmarks.firstOrNull()
            if (active != null) {
                _uiState.update {
                    it.copy(
                        activeProfileName = active.name,
                        activeProfileLat = active.lat,
                        activeProfileLng = active.lng
                    )
                }
            }
        }
    }

    private fun loadPersistenceLayers() {
        viewModelScope.launch(Dispatchers.IO) {
            val restoreAfterBoot = settingsStore.getBoolBlocking("restoreAfterBoot", false)
            _uiState.update { state ->
                state.copy(
                    persistenceLayers = state.persistenceLayers.map { layer ->
                        when (layer.layer) {
                            1 -> layer.copy(active = state.serviceState == MockState.MOCKED)
                            3 -> layer.copy(active = restoreAfterBoot)
                            else -> layer
                        }
                    }
                )
            }
        }
    }

    /** Toggle mocking on/off. When toggling on, activates the foreground service layer. */
    fun toggleMocking() {
        _uiState.update { state ->
            val newServiceState = if (state.isMocking) MockState.NOT_MOCKED else MockState.MOCKED
            state.copy(
                serviceState = newServiceState,
                lastInjectionTime = if (newServiceState == MockState.MOCKED) {
                    System.currentTimeMillis()
                } else {
                    state.lastInjectionTime
                },
                persistenceLayers = state.persistenceLayers.map { layer ->
                    if (layer.layer == 1) layer.copy(active = newServiceState == MockState.MOCKED)
                    else layer
                }
            )
        }
    }

    /** Select a profile by its database ID and reload its data. */
    fun selectProfile(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val bookmarks = bookmarkDao.allSync
            val profile = bookmarks.find { it.id == id }
            if (profile != null) {
                _uiState.update {
                    it.copy(
                        activeProfileName = profile.name,
                        activeProfileLat = profile.lat,
                        activeProfileLng = profile.lng
                    )
                }
            }
        }
    }

    /** Update service state from the bound service's LiveData callback. */
    fun updateServiceState(state: MockState) {
        _uiState.update { it.copy(serviceState = state) }
    }
}
