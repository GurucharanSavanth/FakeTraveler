package cl.coders.faketraveler.aether.ui.observatory

import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A predicted next-app transition with a confidence score.
 */
data class AppPrediction(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val confidence: Float
)

/**
 * A recorded app transition event.
 */
data class AppTransition(
    val fromPackage: String,
    val fromName: String,
    val toPackage: String,
    val toName: String,
    val timestamp: Long
)

/**
 * Immutable UI state for the Observatory screen.
 *
 * [currentApp] — the foreground app package name (null if unknown).
 * [currentAppName] — display name of the foreground app.
 * [currentAppIcon] — icon drawable of the foreground app.
 * [prediction] — the predicted next app transition.
 * [topTransitions] — top 5 most frequent transitions from the current app.
 * [recentTransitions] — last 5 observed transitions.
 * [isObserving] — whether the observatory is actively collecting data.
 */
data class ObservatoryUiState(
    val currentApp: String? = null,
    val currentAppName: String? = null,
    val currentAppIcon: Drawable? = null,
    val prediction: AppPrediction? = null,
    val topTransitions: List<AppPrediction> = emptyList(),
    val recentTransitions: List<AppTransition> = emptyList(),
    val isObserving: Boolean = false
)

/**
 * ViewModel for the Observatory screen.
 *
 * Observes foreground app changes via UsageStatsManager, builds a simple Markov
 * transition matrix, and predicts the next app the user will switch to.
 */
class ObservatoryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ObservatoryUiState())
    val uiState: StateFlow<ObservatoryUiState> = _uiState.asStateFlow()

    /** Transition counts: from_package -> (to_package -> count) */
    private val transitionMatrix = mutableMapOf<String, MutableMap<String, Int>>()
    private val transitions = mutableListOf<AppTransition>()
    private var lastForegroundApp: String? = null

    private val pm: PackageManager = application.packageManager

    /** Start observing foreground app changes. */
    fun startObserving() {
        if (_uiState.value.isObserving) return
        _uiState.update { it.copy(isObserving = true) }

        viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _uiState.value.isObserving) {
                pollForegroundApp()
                delay(2000L)
            }
        }
    }

    /** Stop observing. */
    fun stopObserving() {
        _uiState.update { it.copy(isObserving = false) }
    }

    private fun pollForegroundApp() {
        val app = getApplication<Application>()
        val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return

        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 10_000L

        val currentPkg = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val events = usm.queryEvents(beginTime, endTime)
                var lastPkg: String? = null
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        lastPkg = event.packageName
                    }
                }
                lastPkg
            } else {
                null
            }
        } catch (_: SecurityException) {
            // Usage stats permission not granted
            null
        }

        if (currentPkg != null && currentPkg != lastForegroundApp) {
            val previousApp = lastForegroundApp
            lastForegroundApp = currentPkg

            // Record transition
            if (previousApp != null) {
                recordTransition(previousApp, currentPkg)
            }

            // Update current app info
            val appName = resolveAppName(currentPkg)
            val appIcon = resolveAppIcon(currentPkg)

            // Predict next
            val prediction = predictNext(currentPkg)
            val topPredictions = getTopTransitions(currentPkg)

            _uiState.update {
                it.copy(
                    currentApp = currentPkg,
                    currentAppName = appName,
                    currentAppIcon = appIcon,
                    prediction = prediction,
                    topTransitions = topPredictions,
                    recentTransitions = transitions.takeLast(5).reversed()
                )
            }
        }
    }

    private fun recordTransition(from: String, to: String) {
        val fromMap = transitionMatrix.getOrPut(from) { mutableMapOf() }
        fromMap[to] = (fromMap[to] ?: 0) + 1

        transitions.add(
            AppTransition(
                fromPackage = from,
                fromName = resolveAppName(from),
                toPackage = to,
                toName = resolveAppName(to),
                timestamp = System.currentTimeMillis()
            )
        )

        // Cap stored transitions at 100
        if (transitions.size > 100) {
            transitions.removeAt(0)
        }
    }

    /** Predict the most likely next app from the current app using the Markov matrix. */
    fun predictNext(currentPkg: String): AppPrediction? {
        val fromMap = transitionMatrix[currentPkg] ?: return null
        val total = fromMap.values.sum().toFloat()
        if (total == 0f) return null

        val best = fromMap.maxByOrNull { it.value } ?: return null
        val confidence = best.value / total

        return AppPrediction(
            packageName = best.key,
            appName = resolveAppName(best.key),
            icon = resolveAppIcon(best.key),
            confidence = confidence
        )
    }

    /** Get the top 5 transition targets from the given app. */
    private fun getTopTransitions(currentPkg: String): List<AppPrediction> {
        val fromMap = transitionMatrix[currentPkg] ?: return emptyList()
        val total = fromMap.values.sum().toFloat()
        if (total == 0f) return emptyList()

        return fromMap.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { (pkg, count) ->
                AppPrediction(
                    packageName = pkg,
                    appName = resolveAppName(pkg),
                    icon = resolveAppIcon(pkg),
                    confidence = count / total
                )
            }
    }

    private fun resolveAppName(packageName: String): String {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.')
        }
    }

    private fun resolveAppIcon(packageName: String): Drawable? {
        return try {
            pm.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
