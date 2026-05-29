package cl.coders.faketraveler;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import cl.coders.faketraveler.db.ModuleRepository;
import cl.coders.faketraveler.db.PrivacyWipeLogEntity;

/**
 * Module 7: deletes location traces by category. Operations run in sequence and are NOT rolled back
 * on partial failure (a partial wipe beats no wipe); every step's status is captured in the log's
 * {@code detailsJson}. Module 4 (network) was dropped, so that category is a logged no-op.
 *
 * <p>App-data wipe preserves essential prefs ({@link #KEEP_PREF_KEYS}) so theme/boot behaviour
 * survives. DataStore is left intact (its async clear is out of scope for the synchronous wipe).
 */
public final class PrivacyWipeEngine {

    private static final String TAG = "PrivacyWipeEngine";

    public static final int TYPE_APP_DATA = 0;
    public static final int TYPE_SESSION_HISTORY = 1;
    public static final int TYPE_EXIF_BACKUPS = 2;
    public static final int TYPE_NETWORK_OBS = 3;
    public static final int TYPE_PERMISSION_SNAPSHOTS = 4;
    public static final int TYPE_GEOFENCE_EVENTS = 5;
    public static final int TYPE_FULL = 6;

    private static final Set<String> KEEP_PREF_KEYS = new HashSet<>(Arrays.asList(
            "theme", "prefsSchemaVersion", "restoreAfterBoot"));

    /** Which categories to wipe. */
    public static final class Options {
        public boolean appData;
        public boolean sessionHistory;
        public boolean exifBackups;
        public boolean networkObs;
        public boolean permissions;
        public boolean geofenceEvents;

        public boolean any() {
            return appData || sessionHistory || exifBackups || networkObs || permissions || geofenceEvents;
        }
        public int selectedCount() {
            int n = 0;
            if (appData) n++; if (sessionHistory) n++; if (exifBackups) n++;
            if (networkObs) n++; if (permissions) n++; if (geofenceEvents) n++;
            return n;
        }
    }

    @NonNull private final Context appCtx;
    @NonNull private final ModuleRepository repo;

    public PrivacyWipeEngine(@NonNull Context ctx) {
        this.appCtx = ctx.getApplicationContext();
        this.repo = ModuleRepository.get(appCtx);
    }

    /** Runs the wipe synchronously and writes a {@link PrivacyWipeLogEntity}. */
    @WorkerThread
    @NonNull
    public PrivacyWipeLogEntity wipe(@NonNull Options o) {
        final JSONArray details = new JSONArray();
        boolean allOk = true;

        if (o.sessionHistory) {
            allOk &= step(details, "session_history", () -> {
                repo.getMockSessionDao().deleteAllSessions();
                repo.getRouteDao().deleteAllRoutes();
            });
        }
        if (o.geofenceEvents) {
            allOk &= step(details, "geofence_events", () -> repo.getGeoFenceDao().deleteAllEvents());
        }
        if (o.permissions) {
            allOk &= step(details, "permission_snapshots", () -> {
                repo.getPermissionSnapshotDao().deleteAll();
                repo.getPermissionDriftAlertDao().deleteAll();
            });
        }
        if (o.exifBackups) {
            allOk &= step(details, "exif_log", () -> repo.getExifCleanedDao().deleteAll());
        }
        if (o.networkObs) {
            allOk &= step(details, "network_obs", () -> { /* Module 4 dropped — no data */ });
        }
        if (o.appData) {
            allOk &= step(details, "app_cache", this::clearCache);
            allOk &= step(details, "app_prefs", this::clearNonEssentialPrefs);
        }

        final PrivacyWipeLogEntity log = new PrivacyWipeLogEntity();
        log.wipedAt = System.currentTimeMillis();
        log.wipeType = o.selectedCount() > 1 ? TYPE_FULL : firstType(o);
        log.success = allOk;
        log.detailsJson = details.toString();
        repo.getWipeLogDao().insert(log);
        return log;
    }

    private interface Op { void run() throws Exception; }

    private boolean step(@NonNull JSONArray details, @NonNull String target, @NonNull Op op) {
        boolean ok = true;
        String status = "ok";
        try {
            op.run();
        } catch (Throwable t) {
            ok = false;
            status = "failed: " + t.getClass().getSimpleName();
            Log.w(TAG, "wipe step " + target + " failed", t);
        }
        try {
            final JSONObject row = new JSONObject();
            row.put("target", target);
            row.put("status", status);
            details.put(row);
        } catch (Throwable ignored) {
        }
        return ok;
    }

    private void clearCache() {
        deleteRecursive(appCtx.getCacheDir());
    }

    private void clearNonEssentialPrefs() {
        final SharedPreferences sp =
                appCtx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
        final SharedPreferences.Editor e = sp.edit();
        for (String key : sp.getAll().keySet()) {
            if (!KEEP_PREF_KEYS.contains(key)) e.remove(key);
        }
        e.apply();
    }

    private static void deleteRecursive(File f) {
        if (f == null) return;
        final File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteRecursive(c);
        // Never delete the cache dir itself, only its contents.
        if (!f.isDirectory() || (f.getParentFile() != null
                && !"cache".equals(f.getName()))) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private static int firstType(@NonNull Options o) {
        if (o.appData) return TYPE_APP_DATA;
        if (o.sessionHistory) return TYPE_SESSION_HISTORY;
        if (o.exifBackups) return TYPE_EXIF_BACKUPS;
        if (o.networkObs) return TYPE_NETWORK_OBS;
        if (o.permissions) return TYPE_PERMISSION_SNAPSHOTS;
        if (o.geofenceEvents) return TYPE_GEOFENCE_EVENTS;
        return TYPE_FULL;
    }
}
