package cl.coders.faketraveler;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cl.coders.faketraveler.db.ModuleRepository;
import cl.coders.faketraveler.db.MockSessionDao;
import cl.coders.faketraveler.db.MockSessionEntity;
import cl.coders.faketraveler.db.RoutePointEntity;

/**
 * Module 1: observes {@link MockedLocationService}'s {@code mockEvents} bus and persists each run
 * as a {@link MockSessionEntity} plus a stream of {@link RoutePointEntity} rows.
 *
 * <p>Lifecycle: {@link MainActivity} calls {@link #start} after the service binds and {@link #stop}
 * in {@code disconnectService}. When {@link FeatureFlag#SESSION_HISTORY} is off, {@link #start} is a
 * no-op so nothing is observed or written.
 *
 * <p>Threading: events arrive on the main thread; points are buffered there and flushed in batches
 * (10 points or 5 s, whichever first) through {@link ModuleRepository#io(Runnable)}. The repo's
 * executor is single-threaded, so the session-insert queued on START always completes before any
 * point-insert reads {@link #sessionRowId} — no cross-thread race on that field.
 */
public final class MockSessionRecorder {

    private static final int BATCH_SIZE = 10;
    private static final long BATCH_MS = 5_000L;

    @NonNull private final Context appCtx;
    @NonNull private final ModuleRepository repo;
    @NonNull private final MockSessionDao dao;
    @NonNull private final Observer<MockEvent> observer = this::onEvent;
    @NonNull private final Random random = new Random();

    @Nullable private LiveData<MockEvent> source;

    // Main-thread state for the in-flight session.
    private volatile long sessionRowId = -1L;   // written + read only on the io thread
    private long sessionStartTime;
    private int sequence;
    private boolean haveStart;
    private boolean active;
    private double startLat, startLng, lastLat, lastLng;
    private long lastFlush;
    private boolean altitudeSim, accuracySim;
    @NonNull private final List<RoutePointEntity> buffer = new ArrayList<>();

    public MockSessionRecorder(@NonNull Context ctx) {
        this.appCtx = ctx.getApplicationContext();
        this.repo = ModuleRepository.get(appCtx);
        this.dao = repo.getMockSessionDao();
    }

    /** Begin observing. No-op when the feature flag is disabled. */
    @MainThread
    public void start(@NonNull LiveData<MockEvent> events, @NonNull LifecycleOwner owner) {
        if (!FeatureFlag.SESSION_HISTORY.isEnabled(appCtx)) return;
        final SharedPreferences p =
                appCtx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
        altitudeSim = p.getBoolean("simulateAltitude", false);
        accuracySim = p.getBoolean("simulateAccuracy", false);
        source = events;
        events.observe(owner, observer);
    }

    @MainThread
    public void stop() {
        if (source != null) {
            source.removeObserver(observer);
            source = null;
        }
    }

    @MainThread
    private void onEvent(@Nullable MockEvent e) {
        if (e == null) return;
        switch (e.type) {
            case START: beginSession(e); break;
            case TICK:  recordTick(e);   break;
            case STOP:
            case ERROR: endSession(e, e.type == MockEvent.Type.STOP); break;
            default: break; // PAUSE/RESUME not emitted by the current service
        }
    }

    @MainThread
    private void beginSession(@NonNull MockEvent e) {
        sessionStartTime = e.timestamp;
        sequence = 0;
        haveStart = false;
        buffer.clear();
        lastFlush = e.timestamp;
        sessionRowId = -1L;
        active = true;

        final SharedPreferences p =
                appCtx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
        final String label = p.getString("sessionLabel", "");
        final String resolved = (label == null || label.trim().isEmpty())
                ? "Session " + sessionStartTime : label.trim();
        final long startTime = sessionStartTime;
        repo.io(() -> {
            MockSessionEntity s = new MockSessionEntity();
            s.startTime = startTime;
            s.sessionLabel = resolved;
            s.completed = false;
            sessionRowId = dao.insert(s);
        });
    }

    @MainThread
    private void recordTick(@NonNull MockEvent e) {
        final Location loc = e.location;
        if (loc == null) return;
        if (!active) beginSession(e); // TICK before START (LiveData race) — open a session lazily
        if (!haveStart) {
            startLat = loc.getLatitude();
            startLng = loc.getLongitude();
            haveStart = true;
        }
        lastLat = loc.getLatitude();
        lastLng = loc.getLongitude();

        final RoutePointEntity pt = new RoutePointEntity();
        pt.sequence = sequence++;
        pt.lat = loc.getLatitude();
        pt.lng = loc.getLongitude();
        pt.altitude = altitudeSim
                ? loc.getAltitude() + (random.nextDouble() - 0.5) * 4.0
                : loc.getAltitude();
        pt.accuracy = accuracySim
                ? Math.max(0f, loc.getAccuracy() + (random.nextFloat() - 0.5f) * 6f)
                : loc.getAccuracy();
        pt.timestampOffsetMs = e.timestamp - sessionStartTime;
        buffer.add(pt);

        if (buffer.size() >= BATCH_SIZE || (e.timestamp - lastFlush) >= BATCH_MS) {
            flush(e.timestamp);
        }
    }

    @MainThread
    private void flush(long now) {
        if (buffer.isEmpty()) return;
        final List<RoutePointEntity> snapshot = new ArrayList<>(buffer);
        buffer.clear();
        lastFlush = now;
        repo.io(() -> {
            final long sid = sessionRowId;
            if (sid <= 0) return; // session insert failed; drop rather than orphan
            for (RoutePointEntity p : snapshot) p.sessionId = sid;
            try {
                dao.insertPoints(snapshot);
            } catch (android.database.sqlite.SQLiteException ex) {
                // The parent session row may have vanished mid-recording (e.g. Privacy Wipe
                // cleared session history). Abandon this session instead of crashing FT-ModuleIO;
                // a later START opens a fresh one.
                sessionRowId = -1L;
                android.util.Log.w("MockSessionRecorder", "session row gone; dropping points", ex);
            }
        });
    }

    @MainThread
    private void endSession(@NonNull MockEvent e, boolean completed) {
        flush(e.timestamp);
        if (!haveStart && sequence == 0) {
            // STOP fired with no ticks (e.g. immediate restart) — leave the bare row as-is.
        }
        final long endTime = e.timestamp;
        final double sLat = startLat, sLng = startLng, eLat = lastLat, eLng = lastLng;
        final int count = sequence;
        repo.io(() -> {
            final long sid = sessionRowId;
            if (sid <= 0) return;
            MockSessionEntity s = dao.getSession(sid);
            if (s == null) return;
            s.endTime = endTime;
            s.startLat = sLat;
            s.startLng = sLng;
            s.endLat = eLat;
            s.endLng = eLng;
            s.mockCount = count;
            s.completed = completed;
            dao.update(s);
        });
        active = false;
        sessionRowId = -1L;
    }
}
