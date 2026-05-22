package cl.coders.faketraveler.detection;

import android.app.AppOpsManager;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Approximates the surface a detection-aware target app could inspect to decide whether a
 * fix is real. Runs four cheap checks and returns a coarse risk bucket — this is
 * informational only; a sophisticated server-side check could still flag a low-risk mock.
 */
public final class DetectionEngine {

    public enum Risk { LOW, MEDIUM, HIGH }

    public static final class CheckResult {
        @NonNull public final String label;
        public final boolean passed;
        @NonNull public final String detail;
        public CheckResult(@NonNull String l, boolean p, @NonNull String d) {
            label = l; passed = p; detail = d;
        }
    }

    public static final class Report {
        @NonNull public final Risk risk;
        @NonNull public final List<CheckResult> checks;
        public Report(@NonNull Risk r, @NonNull List<CheckResult> c) { risk = r; checks = c; }
    }

    private DetectionEngine() {}

    @NonNull
    public static Report run(@NonNull Context ctx) {
        final List<CheckResult> out = new ArrayList<>();
        out.add(checkAppOps(ctx));
        out.add(checkAllowMockSetting(ctx));
        out.add(checkAllProvidersMocked(ctx));
        out.add(checkLastLocationTimestamp(ctx));

        int passed = 0;
        for (CheckResult c : out) if (c.passed) passed++;
        final Risk r;
        if (passed >= out.size())                r = Risk.LOW;
        else if (passed >= (out.size() + 1) / 2) r = Risk.MEDIUM;
        else                                      r = Risk.HIGH;
        return new Report(r, out);
    }

    @NonNull
    private static CheckResult checkAppOps(@NonNull Context ctx) {
        boolean granted = false;
        try {
            AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (ops != null) {
                // V21 — the literal string dodges the InlinedApi lint warning that
                // AppOpsManager.OPSTR_MOCK_LOCATION raises.
                granted = ops.checkOpNoThrow("android:mock_location",
                        Process.myUid(), ctx.getPackageName()) == AppOpsManager.MODE_ALLOWED;
            }
        } catch (Throwable ignored) {}
        return new CheckResult("Mock op granted", granted,
                granted ? "OP_MOCK_LOCATION = ALLOWED" : "OP_MOCK_LOCATION = DENIED");
    }

    @SuppressWarnings("deprecation")
    @NonNull
    private static CheckResult checkAllowMockSetting(@NonNull Context ctx) {
        boolean enabled = false;
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                // Pre-M relied on a Settings.Secure flag; M+ moved to the AppOps path
                // checked separately by checkAppOps().
                enabled = !"0".equals(Settings.Secure.getString(
                        ctx.getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION));
            } else {
                enabled = true;
            }
        } catch (Throwable ignored) {}
        return new CheckResult("ALLOW_MOCK_LOCATION", enabled,
                enabled ? "developer setting present" : "developer setting absent");
    }

    @NonNull
    private static CheckResult checkAllProvidersMocked(@NonNull Context ctx) {
        final LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        final boolean gps = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        final boolean network = lm != null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return new CheckResult("All providers up", gps && network,
                "GPS=" + gps + " NETWORK=" + network);
    }

    @NonNull
    private static CheckResult checkLastLocationTimestamp(@NonNull Context ctx) {
        try {
            final LocationManager lm =
                    (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return new CheckResult("Timestamp fresh", false, "no LocationManager");
            @Nullable Location loc;
            try {
                loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } catch (SecurityException se) {
                return new CheckResult("Timestamp fresh", false, "permission denied");
            }
            if (loc == null) return new CheckResult("Timestamp fresh", false, "no fix yet");
            final long ageMs = System.currentTimeMillis() - loc.getTime();
            // A real fix is usually < 5 s old when actively tracking; a stale timestamp
            // is a weak signal that a mock is replaying old fixes.
            final boolean ok = ageMs >= 0L && ageMs < 5_000L;
            return new CheckResult("Timestamp fresh", ok, "age=" + ageMs + "ms");
        } catch (Throwable t) {
            return new CheckResult("Timestamp fresh", false, t.getClass().getSimpleName());
        }
    }
}
