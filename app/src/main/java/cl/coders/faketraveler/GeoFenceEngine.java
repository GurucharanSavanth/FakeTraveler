package cl.coders.faketraveler;

import android.location.Location;

import androidx.annotation.NonNull;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cl.coders.faketraveler.db.GeoFenceEntity;

/**
 * Module 3: point-in-fence evaluation + per-fence state tracking. Circular fences use
 * {@link Location#distanceBetween}; polygonal fences use the even-odd ray-casting rule on the
 * {@code polygonJson} array of {@code [lat,lng]} pairs. Stateful: {@link #evaluate} compares the
 * current containment against the last and emits {@link Transition}s on entry/exit/dwell.
 *
 * <p>Not thread-safe; {@link GeoFenceMonitor} drives it from a single observer callback.
 */
public final class GeoFenceEngine {

    public static final int EVENT_ENTRY = 0;
    public static final int EVENT_EXIT = 1;
    public static final int EVENT_DWELL = 2;

    /** A boundary transition for one fence at one location. */
    public static final class Transition {
        public final long fenceId;
        public final int eventType;
        public Transition(long fenceId, int eventType) {
            this.fenceId = fenceId;
            this.eventType = eventType;
        }
    }

    private final Map<Long, Boolean> inside = new HashMap<>();
    private final Map<Long, Long> enteredAt = new HashMap<>();
    private final Map<Long, Boolean> dwellFired = new HashMap<>();

    public void reset() {
        inside.clear();
        enteredAt.clear();
        dwellFired.clear();
    }

    /**
     * Evaluate one location against the active fences. Returns the transitions to log
     * (respecting each fence's monitorEntry/Exit/Dwell flags).
     */
    @NonNull
    public List<Transition> evaluate(@NonNull List<GeoFenceEntity> fences,
                                     double lat, double lng, long now) {
        final List<Transition> out = new ArrayList<>();
        for (GeoFenceEntity f : fences) {
            final boolean now_in = contains(f, lat, lng);
            final Boolean was = inside.get(f.id);
            final boolean prev_in = was != null && was;

            if (now_in && !prev_in) {
                enteredAt.put(f.id, now);
                dwellFired.put(f.id, false);
                if (f.monitorEntry) out.add(new Transition(f.id, EVENT_ENTRY));
            } else if (!now_in && prev_in) {
                if (f.monitorExit) out.add(new Transition(f.id, EVENT_EXIT));
                enteredAt.remove(f.id);
                dwellFired.put(f.id, false);
            } else if (now_in && f.monitorDwell) {
                final Long since = enteredAt.get(f.id);
                final Boolean fired = dwellFired.get(f.id);
                if (since != null && (fired == null || !fired) && (now - since) >= f.dwellMs) {
                    dwellFired.put(f.id, true);
                    out.add(new Transition(f.id, EVENT_DWELL));
                }
            }
            inside.put(f.id, now_in);
        }
        return out;
    }

    /** Current containment for UI color-coding; null when the fence has not been evaluated yet. */
    public Boolean isInside(long fenceId) {
        return inside.get(fenceId);
    }

    public static boolean contains(@NonNull GeoFenceEntity f, double lat, double lng) {
        if (f.type == 0) {
            final float[] r = new float[1];
            Location.distanceBetween(f.centerLat, f.centerLng, lat, lng, r);
            return r[0] <= f.radiusMeters;
        }
        return pointInPolygon(f.polygonJson, lat, lng);
    }

    /** Even-odd ray casting. {@code polygonJson} = JSON array of [lat,lng] pairs. */
    static boolean pointInPolygon(String polygonJson, double lat, double lng) {
        if (polygonJson == null || polygonJson.isEmpty()) return false;
        try {
            final JSONArray pts = new JSONArray(polygonJson);
            final int n = pts.length();
            if (n < 3) return false;
            boolean in = false;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                final JSONArray a = pts.getJSONArray(i);
                final JSONArray b = pts.getJSONArray(j);
                final double ay = a.getDouble(0), ax = a.getDouble(1);
                final double by = b.getDouble(0), bx = b.getDouble(1);
                final boolean intersect = ((ay > lat) != (by > lat))
                        && (lng < (bx - ax) * (lat - ay) / (by - ay) + ax);
                if (intersect) in = !in;
            }
            return in;
        } catch (Throwable t) {
            return false;
        }
    }
}
