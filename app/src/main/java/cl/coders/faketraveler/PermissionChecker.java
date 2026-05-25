package cl.coders.faketraveler;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Centralised permission / dev-settings checks. FIX-008, FIX-009.
 * No instances; static helpers only.
 */
public final class PermissionChecker {

    private PermissionChecker() {
        throw new UnsupportedOperationException();
    }

    /**
     * True if this app is currently selected as the mock-location app in Developer options.
     * Returns true on API levels where mock op tracking is unavailable (rare; treats as best-effort).
     */
    public static boolean isMockLocationEnabled(@NonNull Context ctx) {
        final AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return true;
        final int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = checkOpModern(ops, ctx);
        } else {
            mode = checkOpLegacy(ops, ctx);
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** {@code AppOpsManager.OPSTR_MOCK_LOCATION} field was added at API 23 (the value is a String
     *  constant that javac inlines, so the field reference is safe at runtime on API 21/22 — but
     *  lint's InlinedApi check flags the field-load. Using the literal eliminates the check
     *  with identical semantics. FIX-022 (drill-sergeant Sin #1). */
    private static final String OP_MOCK_LOCATION = "android:mock_location";

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressWarnings("deprecation") // unsafeCheckOpNoThrow deprecated API 36; no straight replacement for a read-only probe
    private static int checkOpModern(@NonNull AppOpsManager ops, @NonNull Context ctx) {
        return ops.unsafeCheckOpNoThrow(
                OP_MOCK_LOCATION,
                Process.myUid(),
                ctx.getPackageName());
    }

    @SuppressWarnings("deprecation")
    private static int checkOpLegacy(@NonNull AppOpsManager ops, @NonNull Context ctx) {
        return ops.checkOpNoThrow(
                OP_MOCK_LOCATION,
                Process.myUid(),
                ctx.getPackageName());
    }

    /**
     * Show an AlertDialog explaining how to enable mock location and offering a deep-link to
     * Developer settings. Replaces the previous short snackbar (FIX-008).
     */
    public static void showDevSettingsDialog(@NonNull AppCompatActivity activity) {
        final View content = activity.getLayoutInflater()
                .inflate(R.layout.activity_permission_rationale, null, false);
        final AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(content)
                .create();
        content.findViewById(R.id.permission_open_settings)
                .setOnClickListener(v -> {
                    dialog.dismiss();
                    openDevSettings(activity);
                });
        content.findViewById(R.id.permission_already_done)
                .setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void openDevSettings(@NonNull Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            return;
        } catch (ActivityNotFoundException ignored) {
        }
        try {
            a.startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
        } catch (Throwable ignored) {
            // user must navigate manually
        }
    }

    /** True if POST_NOTIFICATIONS is granted (or not required on this API level). */
    public static boolean hasPostNotificationPermission(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Returns the runtime permission string for POST_NOTIFICATIONS, or null on API &lt; 33. */
    public static String postNotificationPermissionName() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.POST_NOTIFICATIONS
                : null;
    }
}
