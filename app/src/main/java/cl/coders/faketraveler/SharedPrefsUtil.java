package cl.coders.faketraveler;

import static cl.coders.faketraveler.MainActivity.sharedPrefKey;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Originally from <a href="https://stackoverflow.com/a/18098090/5894824">StackOverflow</a>.
 * Extended in v2.4 with the v2 schema migration (FIX-014) and location serializers.
 *
 * <p>Legacy backup half of the dual-write contract with {@link SettingsDataStore} (V42).
 * Setters mirror writes into SettingsDataStore; readers prefer DataStore and fall back here
 * only when the key is absent. New code should use {@link SettingsDataStore} directly.
 */
public final class SharedPrefsUtil {

    @NonNull
    private static final String TAG = SharedPrefsUtil.class.getSimpleName();

    public static final int SCHEMA_V2 = 2;
    public static final String KEY_SCHEMA_VERSION = "prefsSchemaVersion";
    public static final String KEY_RESTORE_AFTER_BOOT = "restoreAfterBoot";
    public static final String KEY_LAST_MOCKED_LOCATION = "lastMockedLocation";

    private SharedPrefsUtil() { throw new UnsupportedOperationException(); }

    @NonNull
    public static Editor putDouble(@NonNull Editor edit, @NonNull String key, double value) {
        return edit.putLong(key, Double.doubleToRawLongBits(value));
    }

    public static double getDouble(@NonNull SharedPreferences prefs, @NonNull String key, double defaultValue) {
        return Double.longBitsToDouble(prefs.getLong(key, Double.doubleToLongBits(defaultValue)));
    }

    /** v0 → v1 migration. */
    static void migrateOldPreferences(@NonNull Context context) {
        SharedPreferences oldPrefs =
                context.getSharedPreferences("cl.coders.mockposition.sharedpreferences", Context.MODE_PRIVATE);
        if (!oldPrefs.contains("version")) return;
        Log.i(TAG, "Migrating old config to new format...");
        Editor editor = context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit();
        editor.putInt("version", oldPrefs.getInt("version", 0));
        try { putDouble(editor, "lat", Double.parseDouble(oldPrefs.getString("lat", "12"))); } catch (Throwable ignored) {}
        try { putDouble(editor, "lng", Double.parseDouble(oldPrefs.getString("lng", "15"))); } catch (Throwable ignored) {}
        try { editor.putInt("mockCount", Integer.parseInt(oldPrefs.getString("howManyTimes", "0"))); } catch (Throwable ignored) {}
        try { editor.putInt("mockFrequency", Integer.parseInt(oldPrefs.getString("timeInterval", "10"))); } catch (Throwable ignored) {}
        editor.putLong("endTime", oldPrefs.getLong("endTime", 0));
        editor.apply();
        oldPrefs.edit().clear().apply();
        Log.i(TAG, "Migration done - deleted old config!");
    }

    /** v1 → v2: introduce restoreAfterBoot, lastMockedLocation. Idempotent. FIX-014. */
    public static void migrateToV2(@NonNull Context ctx) {
        final SharedPreferences p = ctx.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);
        final int schema = p.getInt(KEY_SCHEMA_VERSION, 0);
        if (schema >= SCHEMA_V2) return;
        final Editor e = p.edit();
        try {
            if (!p.contains(KEY_RESTORE_AFTER_BOOT)) e.putBoolean(KEY_RESTORE_AFTER_BOOT, false);
            if (!p.contains(KEY_LAST_MOCKED_LOCATION)) e.putString(KEY_LAST_MOCKED_LOCATION, "");
            e.putInt(KEY_SCHEMA_VERSION, SCHEMA_V2);
            e.apply();
            Log.i(TAG, "Migrated SharedPreferences to schema v" + SCHEMA_V2);
        } catch (Throwable t) {
            Log.e(TAG, "v2 migration failed", t);
        }
    }

    public static void saveLastMockedLocation(@NonNull Context ctx, @NonNull Location loc) {
        try {
            final JSONObject o = new JSONObject()
                    .put("lat", loc.getLatitude())
                    .put("lng", loc.getLongitude())
                    .put("ts", System.currentTimeMillis());
            final String json = o.toString();
            ctx.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit()
                    .putString(KEY_LAST_MOCKED_LOCATION, json)
                    .apply();
            SettingsDataStore.get(ctx).setStringBlocking(KEY_LAST_MOCKED_LOCATION, json);
        } catch (JSONException ignored) {
        }
    }

    public static void setRestoreAfterBoot(@NonNull Context ctx, boolean restore) {
        ctx.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_RESTORE_AFTER_BOOT, restore)
                .apply();
        SettingsDataStore.get(ctx).setBoolBlocking(KEY_RESTORE_AFTER_BOOT, restore);
    }

    public static boolean isRestoreAfterBoot(@NonNull Context ctx) {
        // V42: DataStore wins once written. Read DS first; fall back to legacy prefs only
        // when DS is absent.
        final SettingsDataStore ds = SettingsDataStore.get(ctx);
        final android.content.SharedPreferences spv3 = ctx.getApplicationContext()
                .getSharedPreferences(SettingsDataStore.PREF_NAME, Context.MODE_PRIVATE);
        if (spv3.contains(KEY_RESTORE_AFTER_BOOT)) {
            return ds.getBoolBlocking(KEY_RESTORE_AFTER_BOOT, false);
        }
        return ctx.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE)
                .getBoolean(KEY_RESTORE_AFTER_BOOT, false);
    }
}
