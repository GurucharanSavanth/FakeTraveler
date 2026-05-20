package cl.coders.faketraveler;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Detects manufacturer and offers a deep-link to the right battery / autostart screen. FIX-011. */
public final class OemBatteryOptHelper {

    private OemBatteryOptHelper() { throw new UnsupportedOperationException(); }

    public static boolean shouldNag(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        final PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm != null && !pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
    }

    public static void promptIfNeeded(@NonNull AppCompatActivity a) {
        if (!shouldNag(a)) return;
        showDialog(a);
    }

    public static void showDialog(@NonNull AppCompatActivity a) {
        @StringRes final int msg = switch (manufacturer()) {
            case "xiaomi", "redmi", "poco" -> R.string.OemHelper_Xiaomi;
            case "samsung" -> R.string.OemHelper_Samsung;
            case "oneplus", "oppo", "realme" -> R.string.OemHelper_OnePlus;
            case "huawei", "honor" -> R.string.OemHelper_Huawei;
            case "vivo" -> R.string.OemHelper_Vivo;
            default -> R.string.OemHelper_Generic;
        };
        new AlertDialog.Builder(a)
                .setTitle(R.string.OemHelper_Title)
                .setMessage(msg)
                .setPositiveButton(R.string.OemHelper_OpenSettings, (d, w) -> launchSettings(a))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @NonNull
    private static String manufacturer() {
        return Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
    }

    private static void launchSettings(@NonNull Activity a) {
        for (Intent i : oemIntents(a.getPackageName())) {
            try {
                a.startActivity(i);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + a.getPackageName())));
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    private static List<Intent> oemIntents(@NonNull String pkg) {
        final List<Intent> list = new ArrayList<>();
        switch (manufacturer()) {
            case "xiaomi", "redmi", "poco" -> list.add(new Intent().setComponent(new ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity")));
            case "samsung" -> list.add(new Intent().setComponent(new ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity")));
            case "oneplus", "oppo", "realme" -> list.add(new Intent().setComponent(new ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity")));
            case "huawei", "honor" -> list.add(new Intent().setComponent(new ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")));
            default -> { /* fall through to generic */ }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            list.add(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + pkg)));
        }
        return list;
    }
}
