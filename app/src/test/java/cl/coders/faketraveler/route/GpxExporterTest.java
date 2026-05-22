package cl.coders.faketraveler.route;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import cl.coders.faketraveler.GpxImporter;

public class GpxExporterTest {

    @Test public void exports_two_points() {
        GpxImporter.Route r = new GpxImporter.Route(Collections.unmodifiableList(Arrays.asList(
                new GpxImporter.TrackPoint(1.0, 2.0),
                new GpxImporter.TrackPoint(3.0, 4.0)
        )), 1);
        String xml = GpxExporter.toGpxString(r, "TestRoute");
        assertTrue("xml=" + xml, xml.contains("lat=\"1.0\""));
        assertTrue(xml.contains("lon=\"2.0\""));
        assertTrue(xml.contains("lat=\"3.0\""));
        assertTrue(xml.contains("lon=\"4.0\""));
        assertTrue(xml.contains("<name>TestRoute</name>"));
    }

    @Test public void escapes_xml_in_name() {
        GpxImporter.Route r = new GpxImporter.Route(Collections.unmodifiableList(Arrays.asList(
                new GpxImporter.TrackPoint(1.0, 2.0)
        )), 1);
        String xml = GpxExporter.toGpxString(r, "<bad&\"name>");
        assertTrue(xml.contains("&lt;bad&amp;\"name&gt;"));
    }
}
