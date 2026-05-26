package cl.coders.faketraveler.aether.shroud

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cl.coders.faketraveler.aether.model.AetherLocation
import cl.coders.faketraveler.aether.model.ProfileData
import cl.coders.faketraveler.aether.model.ServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Aether foreground service: orchestrates mock-location injection with 7-layer persistence.
 *
 * Lifecycle:
 *   onCreate  -> promoteToForeground (L1) + register stop receiver
 *   onStartCommand -> dispatch ACTION_START / ACTION_STOP / ACTION_RESUME / ACTION_SWITCH_PROFILE
 *   onDestroy -> teardown all persistence layers
 *
 * Persistence layers:
 *   L1 - startForeground: process priority boost
 *   L2 - AlarmManager: 30s heartbeat re-arms the service if killed
 *   L3 - WorkManager: 15m expedited periodic work as ultimate fallback
 *   L6 - Ongoing notification: user-visible anchor preventing swipe-dismiss
 *
 * Mock pipeline per tick:
 *   ProfileResolver -> ForgeEngine.forge -> TemporalSyncEngine.injectAtomic
 *   (engines are constructor-injected stubs until their nodes merge)
 *
 * State is exposed as a [StateFlow] of [ServiceState] for UI binding.
 */
class AetherService : Service() {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    companion object {
        private const val TAG = "AetherService"

        /** Intent actions. */
        const val ACTION_START = "cl.coders.faketraveler.aether.action.START"
        const val ACTION_STOP = "cl.coders.faketraveler.aether.action.STOP"
        const val ACTION_RESUME = "cl.coders.faketraveler.aether.action.RESUME"
        const val ACTION_SWITCH_PROFILE = "cl.coders.faketraveler.aether.action.SWITCH_PROFILE"

        /** Intent extras. */
        const val EXTRA_PROFILE_ID = "cl.coders.faketraveler.aether.extra.PROFILE_ID"

        /** L2 heartbeat interval in milliseconds. */
        private const val HEARTBEAT_INTERVAL_MS = 30_000L

        /** L3 WorkManager periodic interval in minutes. */
        private const val WORK_INTERVAL_MINUTES = 15L

        /** WorkManager unique work name. */
        private const val WORK_NAME = "aether_persistence_heartbeat"

        /** Signature-level permission for pre-33 broadcast registration. */
        private const val STOP_PERMISSION = "cl.coders.faketraveler.permission.STOP_MOCK"
    }

    // ------------------------------------------------------------------
    // Service state
    // ------------------------------------------------------------------

    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)

    /** Observable service state for UI binding. */
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    // ------------------------------------------------------------------
    // Coroutine scope
    // ------------------------------------------------------------------

    private lateinit var serviceScope: CoroutineScope
    private var mockLoopJob: Job? = null

    // ------------------------------------------------------------------
    // Active profile state
    // ------------------------------------------------------------------

    @Volatile
    private var activeProfile: ProfileData? = null

    @Volatile
    private var ticksDone: Int = 0

    @Volatile
    private var ticksTotal: Int = Int.MAX_VALUE

    // ------------------------------------------------------------------
    // Persistence helpers
    // ------------------------------------------------------------------

    private var heartbeatPi: PendingIntent? = null
    private var stopReceiver: StopReceiver? = null
    private var foregroundStarted = false

    // ------------------------------------------------------------------
    // Binder
    // ------------------------------------------------------------------

    private val binder = AetherBinder()

    inner class AetherBinder : Binder() {
        /** The current service state as a cold-readable [StateFlow]. */
        val state: StateFlow<ServiceState> get() = serviceState

        /** Request the service to stop mocking and shut down. */
        fun requestStop() {
            stopMocking()
            stopSelf()
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        promoteToForeground()
        registerStopReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // System restarted us (START_STICKY). Resume if there was an active profile.
            resumeIfPossible()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId != null) {
                    startMocking(profileId)
                } else {
                    Log.w(TAG, "ACTION_START without profile ID; ignoring")
                }
            }
            ACTION_STOP -> {
                stopMocking()
                stopSelf()
            }
            ACTION_RESUME -> {
                resumeIfPossible()
            }
            ACTION_SWITCH_PROFILE -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId != null) {
                    switchProfile(profileId)
                } else {
                    Log.w(TAG, "ACTION_SWITCH_PROFILE without profile ID; ignoring")
                }
            }
            else -> {
                // Unknown action or no action; treat as resume.
                resumeIfPossible()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean = false // stay foreground

    override fun onDestroy() {
        teardownPersistence()
        stopMocking()
        unregisterStopReceiver()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Foreground promotion (L1)
    // ------------------------------------------------------------------

    /**
     * Promote to foreground immediately in onCreate.
     * Uses FOREGROUND_SERVICE_TYPE_DATA_SYNC on API 29+.
     */
    private fun promoteToForeground() {
        AetherNotificationFactory.ensureMockChannel(this)
        val notification = AetherNotificationFactory.buildOngoing(
            context = this,
            profileName = "Idle",
            lat = 0.0,
            lon = 0.0,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    AetherNotificationFactory.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(
                    AetherNotificationFactory.NOTIFICATION_ID,
                    notification,
                )
            }
            foregroundStarted = true
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
            _serviceState.value = ServiceState.Error("Foreground promotion failed: ${t.message}")
            stopSelf()
        }
    }

    // ------------------------------------------------------------------
    // Mock pipeline
    // ------------------------------------------------------------------

    /**
     * Start mocking with the given profile ID.
     *
     * Pipeline per tick:
     *   1. Resolve profile from [profileId] (stub: synthetic profile)
     *   2. ForgeEngine.forge (stub: returns base location)
     *   3. TemporalSyncEngine.injectAtomic (stub: logs injection)
     *   4. Update notification + state
     *
     * The actual engine calls are stubs until W2 nodes merge.
     */
    private fun startMocking(profileId: String) {
        // Cancel any existing loop.
        mockLoopJob?.cancel()
        ticksDone = 0

        _serviceState.value = ServiceState.Starting

        // Resolve the profile. In production this would come from ProfileResolver + Room.
        // For now, create a stub profile so the service is structurally complete.
        val profile = resolveProfile(profileId)
        if (profile == null) {
            _serviceState.value = ServiceState.Error("Profile not found: $profileId")
            return
        }
        activeProfile = profile
        ticksTotal = Int.MAX_VALUE // continuous until stopped

        // Arm persistence layers L2 + L3.
        armHeartbeat()
        armWorkManager()

        // Start the mock loop.
        mockLoopJob = serviceScope.launch {
            runMockLoop(profile)
        }
    }

    /**
     * Core mock loop: runs on [serviceScope], cancellable via [mockLoopJob].
     *
     * Each tick:
     * 1. ForgeEngine.forge(profile) -> AetherLocation  [stub]
     * 2. TemporalSyncEngine.injectAtomic(location)     [stub]
     * 3. Update notification + ServiceState.Active
     */
    private suspend fun runMockLoop(profile: ProfileData) {
        val intervalMs = profile.updateIntervalMs.coerceAtLeast(1000L)

        while (true) {
            val forgeResult = forgeLocation(profile)
            forgeResult.fold(
                onSuccess = { location ->
                    val injectResult = injectLocation(location)
                    injectResult.fold(
                        onSuccess = {
                            ticksDone++
                            _serviceState.value = ServiceState.Active(
                                profileName = profile.name,
                                location = location,
                                ticksDone = ticksDone,
                                ticksTotal = ticksTotal,
                            )
                            refreshNotification(profile.name, location)
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Injection failed", e)
                            _serviceState.value = ServiceState.Error("Inject failed: ${e.message}")
                            postHealthFailure("Mock injection failed: ${e.message}")
                        },
                    )
                },
                onFailure = { e ->
                    Log.e(TAG, "Forge failed", e)
                    _serviceState.value = ServiceState.Error("Forge failed: ${e.message}")
                    postHealthFailure("Location forge failed: ${e.message}")
                },
            )

            delay(intervalMs)
        }
    }

    /**
     * Stub: resolve profile by ID.
     *
     * In production, ProfileResolver reads from Room via Koin-injected DAO.
     * Returns a synthetic profile for structural completeness.
     */
    private fun resolveProfile(profileId: String): ProfileData? {
        // TODO: Replace with ProfileResolver.resolve(profileId) when N10 merges.
        return ProfileData(
            id = profileId,
            name = "Profile-$profileId",
            location = cl.coders.faketraveler.aether.model.GeoPoint(0.0, 0.0),
            altitude = null,
            movementMode = cl.coders.faketraveler.aether.model.MovementMode.STATIONARY,
            targetApps = emptyList(),
            accuracyJitter = true,
            updateIntervalMs = 10_000L,
            createdAt = kotlinx.datetime.Clock.System.now(),
        )
    }

    /**
     * Stub: forge a mock location from the profile.
     *
     * In production: ObservatoryEngine.appEvents -> ProfileResolver -> ForgeEngine.forge.
     */
    private suspend fun forgeLocation(profile: ProfileData): Result<AetherLocation> {
        // TODO: Replace with ForgeEngine.forge(ForgeParams) when N7 merges.
        return Result.success(
            AetherLocation(
                lat = profile.location.lat,
                lon = profile.location.lon,
                altitude = profile.altitude ?: 0.0,
                accuracy = 3.0f,
                speed = 0.0f,
                bearing = 0.0f,
                declination = 0.0,
                satellites = 12,
                provider = cl.coders.faketraveler.aether.model.MockProvider.GPS,
            ),
        )
    }

    /**
     * Stub: inject the forged location atomically across all mock providers.
     *
     * In production: TemporalSyncEngine.injectAtomic(location).
     */
    private suspend fun injectLocation(location: AetherLocation): Result<Unit> {
        // TODO: Replace with TemporalSyncEngine.injectAtomic(location) when N9 merges.
        Log.d(TAG, "Injected: (${location.lat}, ${location.lon}) via ${location.provider}")
        return Result.success(Unit)
    }

    // ------------------------------------------------------------------
    // Profile switching
    // ------------------------------------------------------------------

    /**
     * Hot-swap to a different profile without restarting the service.
     */
    private fun switchProfile(profileId: String) {
        startMocking(profileId) // startMocking cancels the existing loop
    }

    // ------------------------------------------------------------------
    // Resume
    // ------------------------------------------------------------------

    /**
     * Resume mocking if an active profile exists (e.g. after system restart).
     */
    private fun resumeIfPossible() {
        val profile = activeProfile
        if (profile != null) {
            startMocking(profile.id)
        } else {
            // No active profile to resume. Stay foreground but idle so START_STICKY
            // doesn't immediately re-kill us.
            Log.d(TAG, "No active profile to resume; staying idle")
        }
    }

    // ------------------------------------------------------------------
    // Stop
    // ------------------------------------------------------------------

    /**
     * Stop the mock loop and disarm all persistence layers.
     */
    private fun stopMocking() {
        mockLoopJob?.cancel()
        mockLoopJob = null
        activeProfile = null
        ticksDone = 0
        ticksTotal = Int.MAX_VALUE
        _serviceState.value = ServiceState.Idle
        teardownPersistence()
    }

    // ------------------------------------------------------------------
    // Notification refresh
    // ------------------------------------------------------------------

    /**
     * Update the ongoing notification with current profile and coordinates.
     * Throttled internally by NotificationManager (at most 10/s on modern APIs).
     */
    private fun refreshNotification(profileName: String, location: AetherLocation) {
        if (!foregroundStarted) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            nm.notify(
                AetherNotificationFactory.NOTIFICATION_ID,
                AetherNotificationFactory.buildOngoing(this, profileName, location.lat, location.lon),
            )
        }
    }

    /**
     * Post a health-failure notification on the high-importance channel.
     */
    private fun postHealthFailure(reason: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            nm.notify(
                AetherNotificationFactory.HEALTH_NOTIFICATION_ID,
                AetherNotificationFactory.buildHealthFailure(this, reason),
            )
        }
    }

    // ------------------------------------------------------------------
    // L2: AlarmManager heartbeat
    // ------------------------------------------------------------------

    /**
     * Arm a repeating exact alarm every [HEARTBEAT_INTERVAL_MS] that sends
     * [ACTION_RESUME] back to this service. If the process is killed, the
     * alarm fires and restarts us.
     */
    @SuppressLint("ScheduleExactAlarm")
    private fun armHeartbeat() {
        disarmHeartbeat()

        val am = getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(this, AetherService::class.java).apply {
            action = ACTION_RESUME
        }
        val pi = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0,
        )
        heartbeatPi = pi

        am.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + HEARTBEAT_INTERVAL_MS,
            HEARTBEAT_INTERVAL_MS,
            pi,
        )
    }

    private fun disarmHeartbeat() {
        val pi = heartbeatPi ?: return
        val am = getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(pi) }
        heartbeatPi = null
    }

    // ------------------------------------------------------------------
    // L3: WorkManager expedited periodic
    // ------------------------------------------------------------------

    /**
     * Enqueue a 15-minute periodic expedited work request that re-arms the
     * service if all other layers have failed.
     */
    private fun armWorkManager() {
        val request = PeriodicWorkRequestBuilder<AetherPersistenceWorker>(
            WORK_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build(),
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun disarmWorkManager() {
        runCatching { WorkManager.getInstance(this).cancelUniqueWork(WORK_NAME) }
    }

    // ------------------------------------------------------------------
    // Persistence teardown
    // ------------------------------------------------------------------

    private fun teardownPersistence() {
        disarmHeartbeat()
        disarmWorkManager()
    }

    // ------------------------------------------------------------------
    // Stop broadcast receiver
    // ------------------------------------------------------------------

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStopReceiver() {
        val receiver = StopReceiver()
        stopReceiver = receiver
        val filter = IntentFilter(ACTION_STOP)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter, STOP_PERMISSION, null)
            }
        }.onFailure { t ->
            Log.e(TAG, "Failed to register STOP receiver", t)
        }
    }

    private fun unregisterStopReceiver() {
        val receiver = stopReceiver ?: return
        runCatching { unregisterReceiver(receiver) }
        stopReceiver = null
    }

    /**
     * Broadcast receiver for the STOP action from the notification.
     */
    private inner class StopReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_STOP) return
            stopMocking()
            stopSelf()
        }
    }
}
