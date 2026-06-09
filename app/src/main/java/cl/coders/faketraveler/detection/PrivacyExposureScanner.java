package cl.coders.faketraveler.detection;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Inverse of {@link DetectionEngine}: instead of "can a target app tell my GPS is mocked?", asks
 * "is my own device exposing me to surveillance right now?". Three cheap, read-only checks;
 * advisory only — writes no system setting and never alters mocking. Each fired finding carries a
 * {@code settingsAction} the UI deep-links to so the user can fix it themselves.
 */
public final class PrivacyExposureScanner {

    public enum Risk { LOW, MEDIUM, HIGH }

    public static final class CheckResult {
        @NonNull public final String label;
        public final boolean passed;             // true = no exposure from this vector
        @NonNull public final String detail;
        @NonNull public final Risk severity;     // risk when !passed; LOW when passed
        @Nullable public final String settingsAction; // Settings.ACTION_* deep-link, or null

        CheckResult(@NonNull String label, boolean passed, @NonNull String detail,
                    @NonNull Risk severity, @Nullable String settingsAction) {
            this.label = label;
            this.passed = passed;
            this.detail = detail;
            this.severity = severity;
            this.settingsAction = settingsAction;
        }
    }

    public static final class Report {
        @NonNull public final Risk risk;
        @NonNull public final List<CheckResult> checks;
        Report(@NonNull Risk risk, @NonNull List<CheckResult> checks) {
            this.risk = risk;
            this.checks = checks;
        }
    }

    private PrivacyExposureScanner() {
        throw new UnsupportedOperationException();
    }

    @NonNull
    public static Report run(@NonNull Context ctx) {
        final Context app = ctx.getApplicationContext();
        final List<CheckResult> checks = new ArrayList<>();
        checks.add(checkAccessibility(app));
        checks.add(checkAdb(app));
        checks.add(checkOverlayHolders(app));
        Risk max = Risk.LOW;
        for (CheckResult c : checks) {
            if (!c.passed && c.severity.ordinal() > max.ordinal()) max = c.severity;
        }
        return new Report(max, checks);
    }

    @NonNull
    private static CheckResult checkAccessibility(@NonNull Context ctx) {
        final String label = "Accessibility services";
        try {
            final AccessibilityManager am =
                    (AccessibilityManager) ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
            final PackageManager pm = ctx.getPackageManager();
            final List<String> thirdParty = new ArrayList<>();
            if (am != null) {
                for (AccessibilityServiceInfo info : am.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                    if (info.getResolveInfo() == null) continue;
                    final String pkg = info.getResolveInfo().serviceInfo.packageName;
                    if (isSystemPackage(pm, pkg)) continue;
                    thirdParty.add(appLabel(pm, pkg));
                }
            }
            if (thirdParty.isEmpty()) {
                return new CheckResult(label, true,
                        "No third-party app can read your screen.", Risk.LOW, null);
            }
            return new CheckResult(label, false,
                    "Can read your screen and tap for you: " + TextUtils.join(", ", thirdParty),
                    Risk.HIGH, Settings.ACTION_ACCESSIBILITY_SETTINGS);
        } catch (Throwable t) {
            return new CheckResult(label, true, "Could not determine.", Risk.LOW, null);
        }
    }

    @NonNull
    private static CheckResult checkAdb(@NonNull Context ctx) {
        final String label = "USB debugging";
        try {
            final int on = Settings.Global.getInt(
                    ctx.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            if (on != 1) {
                return new CheckResult(label, true, "USB debugging is off.", Risk.LOW, null);
            }
            return new CheckResult(label, false,
                    "USB debugging is on; a connected computer can read this app's data.",
                    Risk.LOW, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        } catch (Throwable t) {
            return new CheckResult(label, true, "Could not determine.", Risk.LOW, null);
        }
    }

    @SuppressWarnings("deprecation") // getInstalledPackages(int)/getApplicationInfo(int) deprecated
    @NonNull                         // API 33; the PackageInfoFlags overloads need API 33 — int form
    private static CheckResult checkOverlayHolders(@NonNull Context ctx) {  // is the minSdk-21 path.
        final String label = "Screen-overlay apps";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return new CheckResult(label, true, "Overlay check needs Android 6+.", Risk.LOW, null);
        }
        try {
            final PackageManager pm = ctx.getPackageManager();
            final AppOpsManager aom =
                    (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            final List<String> holders = new ArrayList<>();
            if (aom != null) {
                for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
                    if (pi.requestedPermissions == null || pi.applicationInfo == null) continue;
                    if (isSystemPackage(pm, pi.packageName)) continue;
                    if (!requests(pi, android.Manifest.permission.SYSTEM_ALERT_WINDOW)) continue;
                    final int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                            pi.applicationInfo.uid, pi.packageName);
                    if (mode == AppOpsManager.MODE_ALLOWED) holders.add(appLabel(pm, pi.packageName));
                }
            }
            final String incomplete = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? " (list may be incomplete on Android 11+)" : "";
            if (holders.isEmpty()) {
                return new CheckResult(label, true,
                        "No third-party app can draw over the screen." + incomplete, Risk.LOW, null);
            }
            return new CheckResult(label, false,
                    "Can draw over the screen: " + TextUtils.join(", ", holders) + incomplete,
                    Risk.MEDIUM, Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        } catch (Throwable t) {
            return new CheckResult(label, true, "Could not determine.", Risk.LOW, null);
        }
    }

    private static boolean requests(@NonNull PackageInfo pi, @NonNull String perm) {
        for (String p : pi.requestedPermissions) if (perm.equals(p)) return true;
        return false;
    }

    @SuppressWarnings("deprecation") // getApplicationInfo(int) — see checkOverlayHolders
    private static boolean isSystemPackage(@NonNull PackageManager pm, @NonNull String pkg) {
        try {
            final ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("deprecation") // getApplicationInfo(int) — see checkOverlayHolders
    @NonNull
    private static String appLabel(@NonNull PackageManager pm, @NonNull String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return pkg;
        }
    }
}
