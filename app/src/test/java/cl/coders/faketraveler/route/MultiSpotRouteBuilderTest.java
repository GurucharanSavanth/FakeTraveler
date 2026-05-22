package cl.coders.faketraveler.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import cl.coders.faketraveler.GpxImporter;

public class MultiSpotRouteBuilderTest {

    @Test public void single_tap_yields_one_point_route() {
        MultiSpotRouteBuilder b = new MultiSpotRouteBuilder();
        b.addTap(1.0, 2.0);
        GpxImporter.Route r = b.build();
        assertEquals(1, r.points().size());
    }

    @Test public void two_distant_taps_interpolate() {
        MultiSpotRouteBuilder b = new MultiSpotRouteBuilder();
        b.setInterpolationStepKm(0.05);
        b.addTap(0.0, 0.0);
        b.addTap(0.01, 0.0);                              // ~1.11 km north
        GpxImporter.Route r = b.build();
        assertTrue("Expected >= 22 interpolated points but got " + r.points().size(),
                r.points().size() >= 22);
    }

    @Test public void clear_resets() {
        MultiSpotRouteBuilder b = new MultiSpotRouteBuilder();
        b.addTap(0, 0);
        b.addTap(1, 1);
        b.clear();
        assertEquals(0, b.size());
    }
}
