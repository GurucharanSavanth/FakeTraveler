package cl.coders.faketraveler;

import android.content.pm.PackageInfo;
import android.os.Build;

import androidx.annotation.NonNull;

/** Version-code extraction across the API 28 split. FIX-015 (extracted from MainActivity). */
public final class PackageInfoUtil {

    private PackageInfoUtil() {
        throw new UnsupportedOperationException();
    }

    public static int versionCode(@NonNull PackageInfo p) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return (int) p.getLongVersionCode();
        }
        return legacyVersionCode(p);
    }

    @SuppressWarnings("deprecation")
    private static int legacyVersionCode(@NonNull PackageInfo p) {
        return p.versionCode;
    }
}
