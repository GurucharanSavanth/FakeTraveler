package cl.coders.faketraveler;

import android.content.Context;

/**
 * Per-feature on/off switches for the P6–P8 module set. Each flag is backed by a boolean in
 * {@link MainActivity#sharedPrefKey} SharedPreferences so toggles in {@code SettingsBottomSheet}
 * read and write the same key. Module 4 (Network Observatory) was intentionally dropped, so it
 * has no flag here.
 */
public enum FeatureFlag {
    SESSION_HISTORY("record_sessions", true),
    ROUTE_LAB("enable_route_lab", true),
    GEOFENCE_LAB("enable_geofence", true),
    PERMISSION_DRIFT("enable_perm_drift", true),
    EXIF_CLEANER("enable_exif_cleaner", false),
    PRIVACY_WIPE("enable_privacy_wipe", true),
    EVIDENCE_EXPORT("enable_evidence_export", true);

    public final String prefKey;
    public final boolean defaultValue;

    FeatureFlag(String prefKey, boolean defaultValue) {
        this.prefKey = prefKey;
        this.defaultValue = defaultValue;
    }

    public boolean isEnabled(Context context) {
        return context.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE)
                .getBoolean(prefKey, defaultValue);
    }

    public void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE)
                .edit().putBoolean(prefKey, enabled).apply();
    }
}
