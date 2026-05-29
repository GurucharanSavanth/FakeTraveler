package cl.coders.faketraveler.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

public class GeoUtilsTest {

    private static final double KM_EPS = 1e-6;
    private static final double KM_ROUGH_EPS = 0.05;

    @Test public void haversine_zero_distance_for_same_point() {
        assertEquals(0.0, GeoUtils.haversineKm(0, 0, 0, 0), KM_EPS);
    }

    @Test public void haversine_zero_for_repeated_non_zero_point() {
        assertEquals(0.0, GeoUtils.haversineKm(-33.45, -70.66, -33.45, -70.66), KM_EPS);
    }

    @Test public void haversine_one_degree_lat_at_equator_is_about_111km() {
        // One degree of latitude ≈ π·R/180 ≈ 111.195 km on a spherical Earth.
        double km = GeoUtils.haversineKm(0.0, 0.0, 1.0, 0.0);
        assertEquals(111.195, km, KM_ROUGH_EPS);
    }

    @Test public void haversine_one_degree_lng_at_equator_is_about_111km() {
        double km = GeoUtils.haversineKm(0.0, 0.0, 0.0, 1.0);
        assertEquals(111.195, km, KM_ROUGH_EPS);
    }

    @Test public void haversine_antipodal_points_is_half_earth_circumference() {
        // Antipode of (0,0) is (0,180). Distance = π·R ≈ 20015.087 km.
        double km = GeoUtils.haversineKm(0.0, 0.0, 0.0, 180.0);
        assertEquals(20015.087, km, 0.5);
    }

    @org.junit.jupiter.api.Test
    public void haversine_near_pole_does_not_blow_up() {
        // Two points 0.001° apart near the pole stay tiny — no NaN/Inf from cos(lat)→0.
        double km = GeoUtils.haversineKm(89.999, 0.0, 89.999, 90.0);
        assertTrue("expected finite km near pole, was " + km,
                !Double.isNaN(km) && !Double.isInfinite(km));
        assertTrue("expected small km near pole, was " + km, km < 1.0);
    }

    @Test public void haversine_returns_zero_for_nan_input() {
        assertEquals(0.0, GeoUtils.haversineKm(Double.NaN, 0, 0, 0), KM_EPS);
        assertEquals(0.0, GeoUtils.haversineKm(0, Double.NaN, 0, 0), KM_EPS);
        assertEquals(0.0, GeoUtils.haversineKm(0, 0, Double.NaN, 0), KM_EPS);
        assertEquals(0.0, GeoUtils.haversineKm(0, 0, 0, Double.NaN), KM_EPS);
    }

    @org.junit.jupiter.api.Test
    public void haversine_returns_zero_for_infinite_input() {
        assertEquals(0.0, GeoUtils.haversineKm(Double.POSITIVE_INFINITY, 0, 0, 0), KM_EPS);
        assertEquals(0.0, GeoUtils.haversineKm(0, Double.NEGATIVE_INFINITY, 0, 0), KM_EPS);
    }

    @Test public void haversine_is_symmetric() {
        double a = GeoUtils.haversineKm(40.7128, -74.0060, 51.5074, -0.1278);
        double b = GeoUtils.haversineKm(51.5074, -0.1278, 40.7128, -74.0060);
        assertEquals(a, b, KM_EPS);
    }

    @Test public void haversine_nyc_to_london_is_about_5570km() {
        // NYC (40.7128, -74.0060) ↔ London (51.5074, -0.1278) ≈ 5570 km (sphere).
        double km = GeoUtils.haversineKm(40.7128, -74.0060, 51.5074, -0.1278);
        assertEquals(5570.0, km, 5.0);
    }
}
