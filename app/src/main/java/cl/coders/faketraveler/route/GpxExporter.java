package cl.coders.faketraveler.route;

import androidx.annotation.NonNull;

import cl.coders.faketraveler.GpxImporter;

/**
 * Serialises a {@link GpxImporter.Route} into a minimal GPX 1.1 XML document. The output
 * round-trips through {@link GpxImporter#parse}.
 */
public final class GpxExporter {

    private GpxExporter() {}

    @NonNull
    public static String toGpxString(@NonNull GpxImporter.Route route, @NonNull String name) {
        final StringBuilder sb = new StringBuilder(256);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"FakeTraveler\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        sb.append("  <trk>\n");
        sb.append("    <name>").append(escape(name)).append("</name>\n");
        sb.append("    <trkseg>\n");
        for (GpxImporter.TrackPoint p : route.points()) {
            sb.append("      <trkpt lat=\"").append(p.lat()).append("\" lon=\"").append(p.lon()).append("\"/>\n");
        }
        sb.append("    </trkseg>\n");
        sb.append("  </trk>\n");
        sb.append("</gpx>\n");
        return sb.toString();
    }

    /** Minimal XML escape — the only attacker-controlled value is the user-supplied
     *  route name, which never appears in attribute position. */
    @NonNull
    private static String escape(@NonNull String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
