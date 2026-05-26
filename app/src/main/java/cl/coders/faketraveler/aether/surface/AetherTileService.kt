package cl.coders.faketraveler.aether.surface

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import cl.coders.faketraveler.MockState
import cl.coders.faketraveler.MockedLocationService

/**
 * Quick Settings tile for toggling mock location on/off.
 *
 * onStartListening: checks current service state and sets tile ACTIVE/INACTIVE.
 * onClick: toggles mock — starts service if inactive, stops if active.
 * If the service died unexpectedly the watchdog resurrects it on toggle.
 */
@RequiresApi(Build.VERSION_CODES.N)
class AetherTileService : TileService() {

    private var binder: MockedLocationService.MockedBinder? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service as MockedLocationService.MockedBinder
            bound = true
            refreshTile()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binder = null
            bound = false
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        bindMockService()
        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        unbindMockService()
    }

    override fun onClick() {
        super.onClick()
        val currentState = binder?.mockState?.value

        if (currentState == MockState.MOCKED) {
            // Stop mock
            stopMock()
        } else {
            // Watchdog resurrect: if service is dead, start fresh via RESUME
            resurrectAndToggle()
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val state = binder?.mockState?.value

        when (state) {
            MockState.MOCKED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Mock Active"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Tap to stop"
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Fake Location"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Tap to resume"
                }
            }
        }
        tile.updateTile()
    }

    private fun stopMock() {
        try {
            binder?.requestStop()
        } catch (t: Throwable) {
            Log.e(TAG, "Binder requestStop failed, sending intent", t)
            val stop = Intent(this, MockedLocationService::class.java)
                .setAction(MockedLocationService.ACTION_STOP)
            startService(stop)
        }
        refreshTile()
    }

    /**
     * Watchdog: if the service died, issue a RESUME foreground start
     * to resurrect it, then bind for state observation.
     */
    private fun resurrectAndToggle() {
        val resume = Intent(this, MockedLocationService::class.java)
            .setAction(MockedLocationService.ACTION_RESUME)
        try {
            ContextCompat.startForegroundService(this, resume)
        } catch (t: Throwable) {
            Log.e(TAG, "resurrect startForegroundService failed", t)
        }
        bindMockService()
        refreshTile()
    }

    private fun bindMockService() {
        if (bound) return
        val intent = Intent(this, MockedLocationService::class.java)
        try {
            bound = bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            Log.e(TAG, "bindService failed", t)
            bound = false
        }
    }

    private fun unbindMockService() {
        if (bound) {
            try {
                unbindService(connection)
            } catch (_: IllegalArgumentException) {
                // Already unbound
            }
            bound = false
        }
        binder = null
    }

    companion object {
        private const val TAG = "AetherTileService"
    }
}
