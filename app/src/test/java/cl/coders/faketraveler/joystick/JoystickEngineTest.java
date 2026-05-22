package cl.coders.faketraveler.joystick;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JoystickEngineTest {

    private static final double EARTH_R_M = 6_371_000.0;

    @Test public void advance_north_moves_lat_up_by_one_meter() {
        JoystickEngine.LatLng next = JoystickEngine.advance(0.0, 0.0, 0.0, 1.0, 1_000L);
        double dLat = next.lat - 0.0;
        double meters = Math.toRadians(dLat) * EARTH_R_M;
        assertEquals(1.0, meters, 0.01);
    }

    @Test public void advance_east_moves_lng_at_equator() {
        JoystickEngine.LatLng next = JoystickEngine.advance(0.0, 0.0, Math.PI / 2.0, 1.0, 1_000L);
        double dLng = next.lng - 0.0;
        double meters = Math.toRadians(dLng) * EARTH_R_M * Math.cos(Math.toRadians(0.0));
        assertEquals(1.0, meters, 0.01);
    }

    @Test public void withinBoundary_returns_false_past_radius() {
        boolean inside = JoystickEngine.withinBoundary(0.0, 0.0, 0.05, 0.0, 1.0);
        assertFalse(inside);
    }

    @Test public void withinBoundary_returns_true_inside_radius() {
        boolean inside = JoystickEngine.withinBoundary(0.0, 0.0, 0.0001, 0.0, 1.0);
        assertTrue(inside);
    }

    @Test public void haversine_zero_distance_for_same_point() {
        assertEquals(0.0, JoystickEngine.haversineKm(0, 0, 0, 0), 1e-9);
    }
}
