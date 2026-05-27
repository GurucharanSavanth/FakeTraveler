package cl.coders.faketraveler.aether.ui.profile

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import cl.coders.faketraveler.db.BookmarkDao
import cl.coders.faketraveler.db.BookmarkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Movement modes for a location profile. */
enum class MovementMode(val label: String) {
    STATIC("Static"),
    DRIFT("Drift"),
    ROUTE("Route")
}

/** Installed app metadata for target-app selection. */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

/**
 * Immutable UI state for the profile editor.
 *
 * [profileId] — null for new profiles, non-null for edits.
 * [name] — user-chosen display name.
 * [lat]/[lng] — coordinates set via map long-press or manual entry.
 * [movementMode] — Static / Drift / Route.
 * [targetApps] — all installed user-facing apps.
 * [selectedApps] — package names this profile targets.
 * [accuracyJitter] — whether to randomize accuracy values.
 * [updateIntervalMs] — mock push interval in milliseconds (100-5000).
 * [isLoading] — true while initial data loads.
 * [isSaved] — set to true after a successful save, consumed by UI to navigate back.
 */
data class ProfileEditorUiState(
    val profileId: Long? = null,
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val movementMode: MovementMode = MovementMode.STATIC,
    val targetApps: List<AppInfo> = emptyList(),
    val selectedApps: Set<String> = emptySet(),
    val accuracyJitter: Boolean = false,
    val updateIntervalMs: Int = 1000,
    val isLoading: Boolean = true,
    val isSaved: Boolean = false
)

/**
 * ViewModel for the Profile Editor screen.
 *
 * Constructor injection via Koin: receives [BookmarkDao] and [SavedStateHandle].
 * The SavedStateHandle provides the profile ID argument for edit mode.
 */
class ProfileEditorViewModel(
    application: Application,
    private val bookmarkDao: BookmarkDao,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProfileEditorUiState())
    val uiState: StateFlow<ProfileEditorUiState> = _uiState.asStateFlow()

    init {
        val profileId = savedStateHandle.get<Long>("profileId")
        if (profileId != null && profileId > 0L) {
            loadExistingProfile(profileId)
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
        loadInstalledApps()
    }

    private fun loadExistingProfile(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val bookmarks = bookmarkDao.allSync
            val profile = bookmarks.find { it.id == id }
            if (profile != null) {
                _uiState.update {
                    it.copy(
                        profileId = profile.id,
                        name = profile.name,
                        lat = profile.lat,
                        lng = profile.lng,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Load all user-facing installed apps for target selection. */
    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { info ->
                    AppInfo(
                        packageName = info.packageName,
                        appName = pm.getApplicationLabel(info).toString(),
                        icon = try {
                            pm.getApplicationIcon(info)
                        } catch (_: Exception) {
                            null
                        }
                    )
                }
                .sortedBy { it.appName.lowercase() }

            _uiState.update { it.copy(targetApps = apps) }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun updateLocation(lat: Double, lng: Double) {
        _uiState.update { it.copy(lat = lat, lng = lng) }
    }

    fun updateMovementMode(mode: MovementMode) {
        _uiState.update { it.copy(movementMode = mode) }
    }

    fun toggleApp(packageName: String) {
        _uiState.update { state ->
            val newSelected = if (packageName in state.selectedApps) {
                state.selectedApps - packageName
            } else {
                state.selectedApps + packageName
            }
            state.copy(selectedApps = newSelected)
        }
    }

    fun updateAccuracyJitter(enabled: Boolean) {
        _uiState.update { it.copy(accuracyJitter = enabled) }
    }

    fun updateIntervalMs(intervalMs: Int) {
        _uiState.update { it.copy(updateIntervalMs = intervalMs.coerceIn(100, 5000)) }
    }

    /** Save or update the profile. Sets [ProfileEditorUiState.isSaved] on success. */
    fun save() {
        val current = _uiState.value
        if (current.name.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val entity = BookmarkEntity().apply {
                if (current.profileId != null) id = current.profileId
                name = current.name
                lat = current.lat
                lng = current.lng
                zoom = 13
                createdAt = System.currentTimeMillis()
            }

            if (current.profileId != null) {
                bookmarkDao.update(entity)
            } else {
                val newId = bookmarkDao.insert(entity)
                _uiState.update { it.copy(profileId = newId) }
            }

            _uiState.update { it.copy(isSaved = true) }
        }
    }

    /** Delete the current profile if it exists. Sets [ProfileEditorUiState.isSaved] to trigger nav. */
    fun delete() {
        val current = _uiState.value
        val id = current.profileId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val entity = BookmarkEntity().apply {
                this.id = id
                name = current.name
                lat = current.lat
                lng = current.lng
                zoom = 13
                createdAt = 0L
            }
            bookmarkDao.delete(entity)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    /** Consume the saved flag after navigation so it does not re-trigger. */
    fun consumeSaved() {
        _uiState.update { it.copy(isSaved = false) }
    }
}
