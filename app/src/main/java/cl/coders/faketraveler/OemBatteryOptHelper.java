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

/**
 * Detects the device manufacturer and routes the user to the right battery / autostart
 * screen. Originally introduced as FIX-011; extended with the {@link Vendor} enum,
 * eight-vendor detection, and per-vendor instruction strings to drive the OEM whitelist
 * card in {@code SettingsBottomSheet} (V33).
 */
public final class OemBatteryOptHelper {

    /** Supported vendors. {@code POCO} and {@code Redmi} resolve to {@code XIAOMI};
     *  {@code iQOO} to {@code VIVO}; {@code HONOR} to {@code HUAWEI}. */
    public enum Vendor {
        XIAOMI, SAMSUNG, ONEPLUS, VIVO, OPPO, REALME, HUAWEI, UNKNOWN
    }

    private OemBatteryOptHelper() { throw new UnsupportedOperationException(); }

    public static boolean shouldNag(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        final PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm != null && !pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
    }

    /** Inverse of {@link #shouldNag(Context)} — exposed so the OEM card can render a
     *  positive status label without a double-negative. */
    public static boolean isWhitelisted(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        final PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
    }

    /** Lower-cased substring match on {@link Build#MANUFACTURER} so model variants and
     *  rebrands (POCO, Redmi, iQOO, HONOR) resolve to a single bucket. */
    @NonNull
    public static Vendor detect() {
        final String m = manufacturer();
        if (m.contains("xiaomi") || m.contains("poco") || m.contains("redmi")) return Vendor.XIAOMI;
        if (m.contains("samsung")) return Vendor.SAMSUNG;
        if (m.contains("oneplus")) return Vendor.ONEPLUS;
        if (m.contains("vivo") || m.contains("iqoo")) return Vendor.VIVO;
        if (m.contains("realme")) return Vendor.REALME;
        if (m.contains("oppo")) return Vendor.OPPO;
        if (m.contains("huawei") || m.contains("honor")) return Vendor.HUAWEI;
        return Vendor.UNKNOWN;
    }

    @NonNull
    public static String getInstructions(@NonNull Context ctx) {
        return switch (detect()) {
            case XIAOMI  -> ctx.getString(R.string.Oem_Xiaomi_Instructions);
            case SAMSUNG -> ctx.getString(R.string.Oem_Samsung_Instructions);
            case ONEPLUS -> ctx.getString(R.string.Oem_OnePlus_Instructions);
            case VIVO    -> ctx.getString(R.string.Oem_Vivo_Instructions);
            case OPPO    -> ctx.getString(R.string.Oem_Oppo_Instructions);
            case REALME  -> ctx.getString(R.string.Oem_Realme_Instructions);
            case HUAWEI  -> ctx.getString(R.string.Oem_Huawei_Instructions);
            default      -> ctx.getString(R.string.Oem_Generic_Instructions);
        };
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
