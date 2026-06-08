package cl.coders.faketraveler;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Quick Settings tile: a one-tap panic control. Broadcasts {@link MockedLocationService#ACTION_STOP}
 * to halt any running mock and clears the persisted mock state so nothing resumes on reboot or
 * process restart.
 *
 * <p>User-initiated only. It performs no background monitoring and changes no system setting — it
 * stops this app's own mock and wipes this app's own coordinates. Available on API 24+ (QS tiles);
 * the manifest entry is ignored by older platforms.
 */
@RequiresApi(Build.VERSION_CODES.N)
public final class PanicTileService extends TileService {

    @NonNull
    private static final String TAG = "PanicTileService";

    @Override
    public void onStartListening() {
        super.onStartListening();
        refreshTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        final Context ctx = getApplicationContext();
        // Broadcast STOP — reaches the live service's runtime receiver if mocking; no-op (and no
        // cold start) when nothing is running. Mirrors the notification's STOP action.
        try {
            ctx.sendBroadcast(new Intent(MockedLocationService.ACTION_STOP)
                    .setPackage(ctx.getPackageName()));
        } catch (Throwable t) {
            Log.w(TAG, "panic stop broadcast failed", t);
        }
        SharedPrefsUtil.clearMockState(ctx);
        refreshTile();
    }

    private void refreshTile() {
        final Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(SharedPrefsUtil.isMockActive(getApplicationContext())
                ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        try {
            tile.updateTile();
        } catch (Throwable ignored) {
            // tile not bound / system busy — visual refresh is best-effort
        }
    }
}
