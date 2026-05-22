package cl.coders.faketraveler.joystick;

import androidx.annotation.NonNull;

/**
 * Pure geometry helpers for joystick-driven movement. No Android dependencies; covered by
 * unit tests in {@code JoystickEngineTest}.
 */
public final class JoystickEngine {

    private static final double EARTH_R_M = 6_371_000.0;

    private JoystickEngine() {}

    public static final class LatLng {
        public final double lat;
        public final double lng;
        public LatLng(double lat, double lng) { this.lat = lat; this.lng = lng; }
    }

    /**
     * Advance a position by {@code speedMs} along {@code bearingRad} for {@code tickMs}.
     *
     * @param bearingRad 0 = north, π/2 = east, π = south, -π/2 = west
     * @param speedMs    metres per second
     * @param tickMs     elapsed milliseconds since the previous tick
     */
    @NonNull
    public static LatLng advance(double lat, double lng, double bearingRad,
                                 double speedMs, long tickMs) {
        if (isNotFinite(lat) || isNotFinite(lng)
                || isNotFinite(bearingRad) || isNotFinite(speedMs)) {
            return new LatLng(0d, 0d);
        }
        final double distM = speedMs * (tickMs / 1000.0);
        final double dLatM = distM * Math.cos(bearingRad);
        final double dLngM = distM * Math.sin(bearingRad);
        final double dLat = Math.toDegrees(dLatM / EARTH_R_M);
        // Longitude degrees shrink with cos(latitude) — divide the east/west metres by
        // EARTH_R_M * cos(lat) to keep the resulting degree step at the right scale.
        // Poles: cos(±90°)≈0 → divide-by-zero. Clamp the denominator floor so step is finite
        // (the user effectively cannot longitude-step at exactly ±90° anyway).
        final double cosLat = Math.cos(Math.toRadians(lat));
        final double denom = Math.max(1e-9, Math.abs(cosLat)) * Math.signum(cosLat == 0 ? 1 : cosLat);
        final double dLng = Math.toDegrees(dLngM / (EARTH_R_M * denom));
        return new LatLng(lat + dLat, lng + dLng);
    }

    public static boolean withinBoundary(double originLat, double originLng,
                                         double curLat, double curLng, double maxKm) {
        if (isNotFinite(maxKm)) return false;
        return haversineKm(originLat, originLng, curLat, curLng) <= maxKm;
    }

    /** Great-circle distance in kilometres between two WGS84 points.
     *  Returns 0 for non-finite inputs (poison-pill exit). */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        if (isNotFinite(lat1) || isNotFinite(lng1)
                || isNotFinite(lat2) || isNotFinite(lng2)) return 0d;
        final double rLat1 = Math.toRadians(lat1);
        final double rLat2 = Math.toRadians(lat2);
        final double dLat = rLat2 - rLat1;
        final double dLng = Math.toRadians(lng2 - lng1);
        final double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(rLat1) * Math.cos(rLat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (EARTH_R_M / 1000.0) * c;
    }

    private static boolean isNotFinite(double v) {
        return Double.isNaN(v) || Double.isInfinite(v);
    }

    /** Common cruising speeds for the joystick UI. Custom slider bypasses this. */
    public enum SpeedMode {
        WALK(1.4), BIKE(5.5), DRIVE(13.9);
        public final double metersPerSecond;
        SpeedMode(double mps) { this.metersPerSecond = mps; }
    }
}
