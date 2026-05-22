package cl.coders.faketraveler;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * In-memory ring buffer of mock-loop events. Capacity {@value #CAPACITY}; oldest entries
 * are dropped FIFO. Thread-safe via a single intrinsic lock — concurrent writers (Timer
 * threads, WorkManager threads, the UI thread) all serialise through it.
 *
 * <p>Listeners are notified outside the lock so a slow consumer cannot stall a writer.
 * The debug console attaches itself as a listener while visible.
 *
 * <p>This buffer is process-local and is cleared on process death — it is intentionally
 * not persisted; the export action lets the user share a snapshot when they need to.
 */
public final class MockLogger {

    public interface Listener {
        void onEntry(@NonNull Entry e);
    }

    public static final class Entry {
        public final long ts;
        @NonNull public final String level;
        @NonNull public final String message;
        Entry(long ts, @NonNull String level, @NonNull String message) {
            this.ts = ts;
            this.level = level;
            this.message = message;
        }
    }

    private static final int CAPACITY = 1000;
    @NonNull private static final Object LOCK = new Object();
    @NonNull private static final ArrayDeque<Entry> RING = new ArrayDeque<>(CAPACITY);
    @NonNull private static final List<Listener> LISTENERS = new ArrayList<>();

    private MockLogger() {}

    public static void log(@NonNull String level, @NonNull String message) {
        final Entry e = new Entry(System.currentTimeMillis(), level, message);
        final List<Listener> snap;
        synchronized (LOCK) {
            if (RING.size() >= CAPACITY) RING.removeFirst();
            RING.addLast(e);
            snap = new ArrayList<>(LISTENERS);
        }
        for (Listener l : snap) {
            try { l.onEntry(e); } catch (Throwable ignored) {}
        }
    }

    public static void addListener(@NonNull Listener l) {
        synchronized (LOCK) { LISTENERS.add(l); }
    }

    public static void removeListener(@NonNull Listener l) {
        synchronized (LOCK) { LISTENERS.remove(l); }
    }

    @NonNull
    public static List<Entry> snapshot() {
        synchronized (LOCK) { return new ArrayList<>(RING); }
    }

    public static void clear() {
        synchronized (LOCK) { RING.clear(); }
    }

    /** Serialises the current buffer to text suitable for {@code Intent.ACTION_SEND}. */
    @NonNull
    public static String exportText() {
        final SimpleDateFormat sdf =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        final StringBuilder sb = new StringBuilder();
        for (Entry e : snapshot()) {
            sb.append(sdf.format(new Date(e.ts)))
                    .append(' ').append(e.level).append(": ").append(e.message).append('\n');
        }
        return sb.toString();
    }
}
