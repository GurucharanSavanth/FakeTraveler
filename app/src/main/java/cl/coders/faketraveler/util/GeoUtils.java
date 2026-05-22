package cl.coders.faketraveler.util;

public final class GeoUtils {

    private static final double EARTH_R_M = 6_371_000.0;

    private GeoUtils() {}

    /** Great-circle distance in kilometres between two WGS84 points. Returns 0 for non-finite inputs. */
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
}
