package cl.coders.faketraveler;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cl.coders.faketraveler.db.ModuleRepository;
import cl.coders.faketraveler.db.PermissionDriftAlertDao;
import cl.coders.faketraveler.db.PermissionDriftAlertEntity;
import cl.coders.faketraveler.db.PermissionSnapshotDao;
import cl.coders.faketraveler.db.PermissionSnapshotEntity;

/**
 * Module 5: enumerates installed packages and their permissions, persists a timestamped snapshot,
 * and diffs it against the previous snapshot to raise {@link PermissionDriftAlertEntity}s.
 *
 * <p>Drift types: 0 added, 1 removed, 2 granted-after-denial, 3 escalated-to-dangerous. Severity:
 * a non-system app newly touching location/camera/mic/contacts = critical(2); storage/phone =
 * warning(1); otherwise info(0). Runs on a worker thread (called from {@link PermissionDriftWorker}).
 */
public final class PermissionScanner {

    private static final int STATUS_DENIED = 0;
    private static final int STATUS_GRANTED = 1;

    private static final Set<String> CRITICAL = new HashSet<>(Arrays.asList(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_CONTACTS"));
    private static final Set<String> WARNING = new HashSet<>(Arrays.asList(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS"));

    @NonNull private final Context ctx;
    @NonNull private final PackageManager pm;
    @NonNull private final PermissionSnapshotDao snapshotDao;
    @NonNull private final PermissionDriftAlertDao alertDao;

    public PermissionScanner(@NonNull Context context) {
        this.ctx = context.getApplicationContext();
        this.pm = ctx.getPackageManager();
        final ModuleRepository repo = ModuleRepository.get(ctx);
        this.snapshotDao = repo.getPermissionSnapshotDao();
        this.alertDao = repo.getPermissionDriftAlertDao();
    }

    /** @return number of new critical alerts raised by this scan. */
    @WorkerThread
    @SuppressWarnings("deprecation")
    public int scan() {
        final long now = System.currentTimeMillis();

        // Previous snapshot keyed by "pkg|perm" -> status.
        final long prevTs = snapshotDao.getLatestTimestamp();
        final Map<String, Integer> prev = new HashMap<>();
        if (prevTs > 0) {
            for (PermissionSnapshotEntity s : snapshotDao.getSnapshotsAt(prevTs)) {
                prev.put(s.packageName + "|" + s.permission, s.status);
            }
        }

        final List<PackageInfo> pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        final List<PermissionSnapshotEntity> snapshots = new ArrayList<>();
        final List<PermissionDriftAlertEntity> alerts = new ArrayList<>();
        final Set<String> currentKeys = new HashSet<>();
        int newCritical = 0;

        for (PackageInfo pi : pkgs) {
            if (pi.requestedPermissions == null) continue;
            final boolean isSystem = pi.applicationInfo != null
                    && (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            final String appName = labelOf(pi.applicationInfo);

            for (int i = 0; i < pi.requestedPermissions.length; i++) {
                final String perm = pi.requestedPermissions[i];
                final boolean granted = pi.requestedPermissionsFlags != null
                        && i < pi.requestedPermissionsFlags.length
                        && (pi.requestedPermissionsFlags[i]
                            & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                final int status = granted ? STATUS_GRANTED : STATUS_DENIED;
                final String key = pi.packageName + "|" + perm;
                currentKeys.add(key);

                final boolean known = prev.containsKey(key);
                final PermissionSnapshotEntity snap = new PermissionSnapshotEntity();
                snap.timestamp = now;
                snap.packageName = pi.packageName;
                snap.appName = appName;
                snap.permission = perm;
                snap.status = status;
                snap.isNew = !known;
                snap.isDangerous = isDangerous(perm);
                snapshots.add(snap);

                if (prevTs > 0) {
                    if (!known) {
                        final int sev = severity(perm, isSystem);
                        alerts.add(alert(now, pi.packageName, appName, perm, 0, sev));
                        if (sev == 2) newCritical++;
                    } else {
                        final int was = prev.get(key);
                        if (was == STATUS_DENIED && status == STATUS_GRANTED) {
                            alerts.add(alert(now, pi.packageName, appName, perm, 2,
                                    Math.max(1, severity(perm, isSystem))));
                        }
                    }
                }
            }
        }

        // Removals: keys present in previous snapshot but not now.
        if (prevTs > 0) {
            for (String key : prev.keySet()) {
                if (!currentKeys.contains(key)) {
                    final int bar = key.indexOf('|');
                    final String pkg = bar > 0 ? key.substring(0, bar) : key;
                    final String perm = bar > 0 ? key.substring(bar + 1) : key;
                    alerts.add(alert(now, pkg, pkg, perm, 1, 0));
                }
            }
        }

        snapshotDao.insertAll(snapshots);
        if (!alerts.isEmpty()) alertDao.insertAll(alerts);
        return newCritical;
    }

    @NonNull
    private static PermissionDriftAlertEntity alert(long now, String pkg, String appName,
                                                    String perm, int driftType, int severity) {
        final PermissionDriftAlertEntity a = new PermissionDriftAlertEntity();
        a.timestamp = now;
        a.packageName = pkg;
        a.appName = appName;
        a.permission = perm;
        a.driftType = driftType;
        a.severity = severity;
        a.acknowledged = false;
        return a;
    }

    private static int severity(@NonNull String perm, boolean isSystem) {
        if (!isSystem && CRITICAL.contains(perm)) return 2;
        if (WARNING.contains(perm)) return 1;
        return 0;
    }

    private boolean isDangerous(@NonNull String perm) {
        if (CRITICAL.contains(perm) || WARNING.contains(perm)) return true;
        try {
            final PermissionInfo info = pm.getPermissionInfo(perm, 0);
            final int level = info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
            return level == PermissionInfo.PROTECTION_DANGEROUS;
        } catch (Throwable t) {
            return false;
        }
    }

    @NonNull
    private String labelOf(ApplicationInfo ai) {
        if (ai == null) return "";
        try {
            final CharSequence l = pm.getApplicationLabel(ai);
            return l == null ? "" : l.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
