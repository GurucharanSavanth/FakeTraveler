package cl.coders.faketraveler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Re-launches the mock-location foreground service after a reboot, if the user opted in
 * via {@code restoreAfterBoot} and a previous mock had not yet expired. FIX-005.
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    @NonNull
    private static final String TAG = BootCompletedReceiver.class.getSimpleName();

    @Override
    public void onReceive(@NonNull Context ctx, @NonNull Intent intent) {
        final String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            return;
        }

        final SharedPreferences p =
                ctx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
        if (!p.getBoolean("restoreAfterBoot", false)) {
            return;
        }

        final long endTime = p.getLong("endTime", 0L);
        if (endTime <= System.currentTimeMillis()) {
            return;
        }

        if (!PermissionChecker.isMockLocationEnabled(ctx)) {
            Log.w(TAG, "Skipping boot resume: mock location is no longer enabled");
            return;
        }

        try {
            final Intent svc = new Intent(ctx, MockedLocationService.class)
                    .setAction(MockedLocationService.ACTION_RESUME);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(ctx, svc);
            } else {
                ctx.startService(svc);
            }
            Log.i(TAG, "Mock service resume requested after boot");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to resume mock service on boot", t);
        }
    }
}
