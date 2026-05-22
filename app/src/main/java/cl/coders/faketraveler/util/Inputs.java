package cl.coders.faketraveler.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Poison-pill validators applied at every untrusted boundary (Intent extras, JS bridge,
 * SharedPreferences, JSON, EditText, geo: URIs). Each helper either returns a sanitized
 * value or signals "reject" via a sentinel so the caller can short-circuit.
 *
 * <p>No Android dependencies — unit-testable as pure Java.
 */
public final class Inputs {

    public static final double LAT_MIN = -90d;
    public static final double LAT_MAX = 90d;
    public static final double LNG_MIN = -180d;
    public static final double LNG_MAX = 180d;
    public static final long FREQ_MIN_MS = 250L;
    public static final long FREQ_MAX_MS = 24L * 60L * 60L * 1000L; // 24h
    public static final int COUNT_MAX = 1_000_000;
    public static final float SPEED_MAX_MS = 1_000f;

    private Inputs() { throw new UnsupportedOperationException(); }

    public static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    public static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    public static boolean validLat(double v) {
        return isFinite(v) && v >= LAT_MIN && v <= LAT_MAX;
    }

    public static boolean validLng(double v) {
        return isFinite(v) && v >= LNG_MIN && v <= LNG_MAX;
    }

    public static double clampLat(double v) {
        if (!isFinite(v)) return 0d;
        if (v < LAT_MIN) return LAT_MIN;
        if (v > LAT_MAX) return LAT_MAX;
        return v;
    }

    public static double clampLng(double v) {
        if (!isFinite(v)) return 0d;
        if (v < LNG_MIN) return LNG_MIN;
        if (v > LNG_MAX) return LNG_MAX;
        return v;
    }

    public static long clampFreqMs(long ms) {
        if (ms < FREQ_MIN_MS) return FREQ_MIN_MS;
        if (ms > FREQ_MAX_MS) return FREQ_MAX_MS;
        return ms;
    }

    public static int clampCount(int n) {
        if (n < 0) return 0;
        if (n > COUNT_MAX) return COUNT_MAX;
        return n;
    }

    public static float sanitizeSpeed(float s) {
        if (!isFinite(s)) return 0f;
        if (s < 0f) return 0f;
        if (s > SPEED_MAX_MS) return SPEED_MAX_MS;
        return s;
    }

    public static double sanitizeDelta(double d) {
        if (!isFinite(d)) return 0d;
        return d;
    }

    /** Parse {@code s} as double, return {@code def} on null/blank/NumberFormatException
     *  or non-finite result. Safe to call on EditText, JSON strings, URI parts. */
    public static double parseDoubleSafe(@Nullable String s, double def) {
        if (s == null) return def;
        final String t = s.trim();
        if (t.isEmpty() || "-".equals(t) || "+".equals(t)) return def;
        try {
            final double v = Double.parseDouble(t);
            return isFinite(v) ? v : def;
        } catch (NumberFormatException nfe) {
            return def;
        }
    }

    public static int parseIntSafe(@Nullable String s, int def) {
        if (s == null) return def;
        final String t = s.trim();
        if (t.isEmpty() || "-".equals(t) || "+".equals(t)) return def;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException nfe) {
            return def;
        }
    }

    /** Saturating multiply for endTime computation. Returns {@code Long.MAX_VALUE} on overflow.
     *  Inputs assumed non-negative; negative inputs return 0. */
    public static long saturatingMul(long a, long b) {
        if (a < 0 || b < 0) return 0L;
        if (a == 0L || b == 0L) return 0L;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }

    public static long saturatingAdd(long a, long b) {
        if (a >= 0 && b > Long.MAX_VALUE - a) return Long.MAX_VALUE;
        if (a < 0 && b < Long.MIN_VALUE - a) return Long.MIN_VALUE;
        return a + b;
    }

    /** Render a double for embedding in a {@code javascript:} URI. Guarantees the output is
     *  syntactically a JS number literal (no {@code NaN} / {@code Infinity} which would
     *  parse as identifiers and silently break the call site). */
    @NonNull
    public static String jsNumber(double v) {
        if (!isFinite(v)) return "0";
        return Double.toString(v);
    }

    /** Fail-fast accessor for views inflated by {@code setContentView}. Throws an
     *  IllegalStateException with the id name baked into the message when the lookup
     *  returns null — better diagnostic than the bare NPE that {@code findViewById(...)
     *  .setOnClickListener(...)} produces if the layout drifts. */
    @NonNull
    public static <T extends android.view.View> T requireView(
            @NonNull android.app.Activity activity, int id, @NonNull String name) {
        final T v = activity.findViewById(id);
        if (v == null) throw new IllegalStateException("missing view: " + name);
        return v;
    }

    @NonNull
    public static <T extends android.view.View> T requireView(
            @NonNull android.view.View root, int id, @NonNull String name) {
        final T v = root.findViewById(id);
        if (v == null) throw new IllegalStateException("missing view: " + name);
        return v;
    }
}
