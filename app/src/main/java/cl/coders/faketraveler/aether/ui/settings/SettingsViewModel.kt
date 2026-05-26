package cl.coders.faketraveler.aether.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.coders.faketraveler.SettingsDataStore
import cl.coders.faketraveler.SharedPrefsUtil
import cl.coders.faketraveler.db.AppDatabase
import cl.coders.faketraveler.db.BookmarkDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** App theme options. */
enum class AppTheme(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

/**
 * Immutable UI state for the Settings screen.
 *
 * [theme] — selected app theme.
 * [chameleonEnabled] — whether the chameleon (dynamic appearance) mode is on.
 * [restoreAfterBoot] — whether to resume mocking after device reboot.
 * [oemExempt] — whether the app is whitelisted from OEM battery optimization.
 * [cacheSize] — human-readable string of cached data size (e.g. "2.4 MB").
 */
data class SettingsUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val chameleonEnabled: Boolean = false,
    val restoreAfterBoot: Boolean = false,
    val oemExempt: Boolean = false,
    val cacheSize: String = "0 B"
)

/**
 * ViewModel for the Settings screen.
 *
 * Constructor injection via Koin: receives [BookmarkDao].
 * Reads/writes to [SettingsDataStore] for persistence.
 */
class SettingsViewModel(
    application: Application,
    private val bookmarkDao: BookmarkDao
) : AndroidViewModel(application) {

    private val settingsStore = SettingsDataStore.get(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()

            val themeStr = settingsStore.getStringBlocking("app_theme")
            val theme = try {
                if (themeStr != null) AppTheme.valueOf(themeStr) else AppTheme.SYSTEM
            } catch (_: IllegalArgumentException) {
                AppTheme.SYSTEM
            }

            val chameleon = settingsStore.getBoolBlocking("chameleon_enabled", false)
            val restoreAfterBoot = SharedPrefsUtil.isRestoreAfterBoot(app)
            val oemExempt = try {
                cl.coders.faketraveler.OemBatteryOptHelper.isWhitelisted(app)
            } catch (_: Exception) {
                false
            }

            // Estimate cache size
            val cacheDir = app.cacheDir
            val cacheSizeBytes = cacheDir?.let { calculateDirSize(it) } ?: 0L
            val cacheSizeStr = formatSize(cacheSizeBytes)

            _uiState.update {
                SettingsUiState(
                    theme = theme,
                    chameleonEnabled = chameleon,
                    restoreAfterBoot = restoreAfterBoot,
                    oemExempt = oemExempt,
                    cacheSize = cacheSizeStr
                )
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        _uiState.update { it.copy(theme = theme) }
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setStringBlocking("app_theme", theme.name)
        }
    }

    fun setChameleonEnabled(enabled: Boolean) {
        _uiState.update { it.copy(chameleonEnabled = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setBoolBlocking("chameleon_enabled", enabled)
        }
    }

    fun setRestoreAfterBoot(enabled: Boolean) {
        _uiState.update { it.copy(restoreAfterBoot = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            SharedPrefsUtil.setRestoreAfterBoot(getApplication(), enabled)
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = getApplication<Application>().cacheDir
            cacheDir?.deleteRecursively()
            _uiState.update { it.copy(cacheSize = "0 B") }
        }
    }

    fun clearProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkDao.deleteAll()
        }
    }

    fun clearMarkovData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Clear any stored transition data from DataStore
            settingsStore.setStringBlocking("markov_transitions", null)
        }
    }

    private fun calculateDirSize(dir: java.io.File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { calculateDirSize(it) } ?: 0L
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
