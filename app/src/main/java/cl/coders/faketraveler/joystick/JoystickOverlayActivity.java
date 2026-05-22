package cl.coders.faketraveler.joystick;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import cl.coders.faketraveler.MockedLocationService;
import cl.coders.faketraveler.PermissionChecker;

/**
 * Transparent entry point for the joystick overlay. Routes the user to the system overlay
 * permission screen when {@code SYSTEM_ALERT_WINDOW} is not granted, requests
 * {@link Manifest#permission_group#LOCATION ACCESS_COARSE_LOCATION} on API 34+, and verifies
 * the app is the selected mock-location app before starting {@link JoystickService}.
 */
public class JoystickOverlayActivity extends AppCompatActivity {

    @NonNull
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) maybeStartService();
                else finish();
            });

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
        if (!PermissionChecker.isMockLocationEnabled(this)) {
            PermissionChecker.showDevSettingsDialog(this);
            // Dialog buttons own the user flow from here; finish so we don't leak the
            // transparent host activity while the dialog persists state via dev settings.
            finish();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            try {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
            } catch (Throwable t) {
                Log.w("JoystickOverlay", "Could not request location permission", t);
                finish();
            }
            return;
        }
        maybeStartService();
    }

    private void maybeStartService() {
        // Stop any active timed mock so its timer doesn't fight the joystick over the same
        // test providers (they would race on setTestProviderLocation calls otherwise).
        try {
            stopService(new Intent(this, MockedLocationService.class));
        } catch (Throwable ignored) {}

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
