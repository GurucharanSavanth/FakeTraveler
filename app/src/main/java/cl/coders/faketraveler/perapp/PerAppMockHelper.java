package cl.coders.faketraveler.perapp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import cl.coders.faketraveler.OemBatteryOptHelper;
import cl.coders.faketraveler.R;

/**
 * Surfaces documented per-app mock workarounds (Samsung Secure Folder, Xiaomi Dual Apps,
 * OnePlus Parallel Apps, Work Profile, the Island app on F-Droid). The app itself does not
 * implement per-app spoofing — Android's mock-location is system-wide; bypassing that
 * would require private APIs or root (V8/V20). Every deep-link returned here is a public
 * {@link Intent}; the helper deliberately avoids reflection, shell, and private APIs
 * (V44).
 */
public final class PerAppMockHelper {

    public static final class Approach {
        @NonNull public final String title;
        @NonNull public final String body;
        @Nullable public final Intent deepLink;
        public Approach(@NonNull String t, @NonNull String b, @Nullable Intent i) {
            title = t; body = b; deepLink = i;
        }
    }

    private PerAppMockHelper() {}

    @NonNull
    public static List<Approach> getApproachesFor(@NonNull Context ctx) {
        final List<Approach> out = new ArrayList<>();
        final OemBatteryOptHelper.Vendor v = OemBatteryOptHelper.detect();

        if (v == OemBatteryOptHelper.Vendor.SAMSUNG) {
            out.add(new Approach(
                    ctx.getString(R.string.PerApp_Samsung_Title),
                    ctx.getString(R.string.PerApp_Samsung_Body),
                    safe(new Intent(Settings.ACTION_PRIVACY_SETTINGS))));
        }
        if (v == OemBatteryOptHelper.Vendor.XIAOMI) {
            out.add(new Approach(
                    ctx.getString(R.string.PerApp_Xiaomi_Title),
                    ctx.getString(R.string.PerApp_Xiaomi_Body),
                    appDetails(ctx)));
        }
        if (v == OemBatteryOptHelper.Vendor.ONEPLUS) {
            out.add(new Approach(
                    ctx.getString(R.string.PerApp_OnePlus_Title),
                    ctx.getString(R.string.PerApp_OnePlus_Body),
                    appDetails(ctx)));
        }
        // Work Profile + Island work everywhere, so they are always offered after the
        // OEM-specific entry.
        out.add(new Approach(
                ctx.getString(R.string.PerApp_WorkProfile_Title),
                ctx.getString(R.string.PerApp_WorkProfile_Body),
                safe(new Intent(Settings.ACTION_SYNC_SETTINGS))));
        out.add(new Approach(
                ctx.getString(R.string.PerApp_Island_Title),
                ctx.getString(R.string.PerApp_Island_Body),
                new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://f-droid.org/packages/com.oasisfeng.island/"))));
        return out;
    }

    @NonNull
    private static Intent appDetails(@NonNull Context ctx) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + ctx.getPackageName()));
    }

    /** Adds NEW_TASK so the deep-link fires from a non-activity context if needed. */
    @NonNull
    private static Intent safe(@NonNull Intent i) {
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
