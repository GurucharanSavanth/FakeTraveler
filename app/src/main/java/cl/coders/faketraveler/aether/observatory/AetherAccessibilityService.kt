package cl.coders.faketraveler.aether.observatory

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Low-privilege accessibility service used as a **fallback** for foreground-app
 * detection when the user has not granted UsageStats permission.
 *
 * Observes [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] events only.
 * Extracts the `packageName` and broadcasts it via both:
 *   1. A [SharedFlow] for in-process consumers (preferred).
 *   2. A [LocalBroadcastManager] intent for legacy / cross-component listeners.
 *
 * **Google Play compliance notes**:
 *   - No node traversal (`AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS` NOT set).
 *   - No content capture or text extraction.
 *   - Minimal `intent-filter`: only `TYPE_WINDOW_STATE_CHANGED`.
 *   - Manifest entry uses explicit `android:permission` attribute to prevent
 *     third-party binding.
 */
class AetherAccessibilityService : AccessibilityService() {

    companion object {
        /** Action broadcast via [LocalBroadcastManager] on each foreground transition. */
        const val ACTION_FOREGROUND_CHANGED =
            "cl.coders.faketraveler.aether.ACTION_FOREGROUND_CHANGED"

        /** Intent extra: the package name that moved to the foreground. */
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        /**
         * In-process flow of foreground package names.
         *
         * [MutableSharedFlow] with replay = 1 so late collectors receive the
         * most recent foreground app immediately.  `extraBufferCapacity = 0`
         * because we only care about the latest value (callers apply
         * `distinctUntilChanged` upstream).
         */
        private val _foregroundPackage = MutableSharedFlow<String>(
            replay = 1,
            extraBufferCapacity = 0,
        )

        /** Public read-only projection of [_foregroundPackage]. */
        val foregroundPackage: SharedFlow<String> = _foregroundPackage.asSharedFlow()
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // No FLAG_RETRIEVE_INTERACTIVE_WINDOWS — we never touch the node tree.
            flags = 0
            notificationTimeout = ObservatoryConstants.POLL_INTERVAL_MS
        }
    }

    // ------------------------------------------------------------------
    // Event handling
    // ------------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg.isBlank()) return

        // Emit to in-process SharedFlow (non-suspending tryEmit; drops if full — acceptable
        // because we only care about the latest package).
        _foregroundPackage.tryEmit(pkg)

        // Broadcast for cross-component listeners.
        val intent = Intent(ACTION_FOREGROUND_CHANGED).apply {
            putExtra(EXTRA_PACKAGE_NAME, pkg)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onInterrupt() {
        // Required override — nothing to clean up.
    }
}
