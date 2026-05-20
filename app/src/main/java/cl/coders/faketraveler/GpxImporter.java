package cl.coders.faketraveler;

import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Streaming GPX (&lt;trkpt&gt;) parser + JSON persister. FIX-012.
 * Safe by construction: {@link Xml#newPullParser()} does not process DTDs, so XXE attacks
 * are out of reach. Caps point count at {@link #MAX_POINTS} to bound memory.
 */
public final class GpxImporter {

    public static final int MAX_POINTS = 100_000;

    public record TrackPoint(double lat, double lon) {}

    public record Route(@NonNull List<TrackPoint> points, int version) {}

    private GpxImporter() { throw new UnsupportedOperationException(); }

    /**
     * @param in stream of GPX XML (caller closes)
     * @return parsed Route (possibly empty)
     * @throws IOException if size cap is exceeded or stream is unreadable
     * @throws XmlPullParserException on malformed XML
     */
    @NonNull
    public static Route parse(@NonNull InputStream in) throws IOException, XmlPullParserException {
        final XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(in, null);
        final List<TrackPoint> pts = new ArrayList<>();
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev != XmlPullParser.START_TAG) continue;
            if (!"trkpt".equals(p.getName())) continue;
            final String lat = p.getAttributeValue(null, "lat");
            final String lon = p.getAttributeValue(null, "lon");
            if (lat == null || lon == null) continue;
            try {
                pts.add(new TrackPoint(Double.parseDouble(lat), Double.parseDouble(lon)));
            } catch (NumberFormatException ignored) {
                // skip malformed point
            }
            if (pts.size() > MAX_POINTS) {
                throw new IOException("GPX too large: > " + MAX_POINTS + " points");
            }
        }
        return new Route(Collections.unmodifiableList(pts), 1);
    }

    @NonNull
    public static String toJson(@NonNull Route r) {
        final JSONArray arr = new JSONArray();
        for (TrackPoint t : r.points()) {
            final JSONObject o = new JSONObject();
            try {
                o.put("lat", t.lat());
                o.put("lng", t.lon());
            } catch (JSONException ignored) {
                continue;
            }
            arr.put(o);
        }
        try {
            return new JSONObject()
                    .put("version", r.version())
                    .put("points", arr)
                    .toString();
        } catch (JSONException e) {
            return "{\"version\":1,\"points\":[]}";
        }
    }

    @Nullable
    public static Route fromJson(@NonNull String json) {
        if (json.isEmpty()) return null;
        try {
            final JSONObject root = new JSONObject(json);
            final JSONArray arr = root.optJSONArray("points");
            if (arr == null) return null;
            final List<TrackPoint> pts = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                final JSONObject o = arr.getJSONObject(i);
                pts.add(new TrackPoint(o.getDouble("lat"), o.getDouble("lng")));
            }
            return new Route(Collections.unmodifiableList(pts), root.optInt("version", 1));
        } catch (JSONException e) {
            return null;
        }
    }
}
