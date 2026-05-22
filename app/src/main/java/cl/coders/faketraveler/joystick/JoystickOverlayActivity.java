package cl.coders.faketraveler.joystick;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Transparent entry point for the joystick overlay. Routes the user to the system overlay
 * permission screen when {@code SYSTEM_ALERT_WINDOW} is not granted; otherwise starts
 * {@link JoystickService} and finishes.
 */
public class JoystickOverlayActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable ignored) {
                // Some OEMs hide the overlay-permission screen; app-details is the fallback.
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
            finish();
            return;
        }
        final Intent svc = new Intent(this, JoystickService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, svc);
            } else {
                startService(svc);
            }
        } catch (Throwable t) {
            Log.e("JoystickOverlay", "startForegroundService failed", t);
        }
        finish();
    }
}
