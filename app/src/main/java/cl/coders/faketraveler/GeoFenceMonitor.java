package cl.coders.faketraveler;

import android.content.Context;
import android.location.Location;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.List;

import cl.coders.faketraveler.db.GeoFenceDao;
import cl.coders.faketraveler.db.GeoFenceEntity;
import cl.coders.faketraveler.db.GeoFenceEventEntity;
import cl.coders.faketraveler.db.ModuleRepository;

/**
 * Module 3: feeds each mock TICK into {@link GeoFenceEngine} and persists the resulting
 * {@link GeoFenceEventEntity} rows. Active fences are loaded once on {@link #start} (and on each
 * START event) into a volatile snapshot read on the main thread; DB writes go through
 * {@link ModuleRepository#io(Runnable)}. No-op when {@link FeatureFlag#GEOFENCE_LAB} is disabled.
 */
public final class GeoFenceMonitor {

    @NonNull private final Context appCtx;
    @NonNull private final ModuleRepository repo;
    @NonNull private final GeoFenceDao dao;
    @NonNull private final GeoFenceEngine engine = new GeoFenceEngine();
    @NonNull private final Observer<MockEvent> observer = this::onEvent;

    @Nullable private LiveData<MockEvent> source;
    @NonNull private volatile List<GeoFenceEntity> activeFences = new ArrayList<>();

    public GeoFenceMonitor(@NonNull Context ctx) {
        this.appCtx = ctx.getApplicationContext();
        this.repo = ModuleRepository.get(appCtx);
        this.dao = repo.getGeoFenceDao();
    }

    @MainThread
    public void start(@NonNull LiveData<MockEvent> events, @NonNull LifecycleOwner owner) {
        if (!FeatureFlag.GEOFENCE_LAB.isEnabled(appCtx)) return;
        reloadFences();
        source = events;
        events.observe(owner, observer);
    }

    @MainThread
    public void stop() {
        if (source != null) {
            source.removeObserver(observer);
            source = null;
        }
        engine.reset();
    }

    /** Re-read active fences from the DB (call after the user edits fences). */
    public void reloadFences() {
        repo.io(() -> activeFences = new ArrayList<>(dao.getActiveGeoFences()));
    }

    @NonNull
    public GeoFenceEngine engine() { return engine; }

    @MainThread
    private void onEvent(@Nullable MockEvent e) {
        if (e == null) return;
        if (e.type == MockEvent.Type.START) {
            engine.reset();
            reloadFences();
            return;
        }
        if (e.type != MockEvent.Type.TICK || e.location == null) return;

        final Location loc = e.location;
        final long sessionId = e.sessionId;
        final List<GeoFenceEntity> fences = activeFences; // volatile snapshot
        if (fences.isEmpty()) return;

        final List<GeoFenceEngine.Transition> transitions =
                engine.evaluate(fences, loc.getLatitude(), loc.getLongitude(), e.timestamp);
        if (transitions.isEmpty()) return;

        final double lat = loc.getLatitude(), lng = loc.getLongitude();
        final long ts = e.timestamp;
        repo.io(() -> {
            for (GeoFenceEngine.Transition tr : transitions) {
                final GeoFenceEventEntity ev = new GeoFenceEventEntity();
                ev.geofenceId = tr.fenceId;
                ev.eventType = tr.eventType;
                ev.timestamp = ts;
                ev.triggeredLat = lat;
                ev.triggeredLng = lng;
                ev.sessionId = sessionId;
                dao.insertEvent(ev);
            }
        });
    }
}
