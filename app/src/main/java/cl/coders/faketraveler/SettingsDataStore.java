package cl.coders.faketraveler;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Application-wide settings store with LiveData observers.
 *
 * <p>Public surface: blocking get/set + LiveData per key. Implementation is backed by a
 * dedicated SharedPreferences file ({@value #PREF_NAME}) — the
 * {@code androidx.datastore-preferences} dependency is declared so the backend can be
 * swapped without changing call sites.
 *
 * <p>Acts as the primary half of the dual-write contract with {@link SharedPrefsUtil}:
 * once a key has been written here it is authoritative; legacy SharedPreferences is read
 * only as a fallback when the key is absent (V42).
 */
public final class SettingsDataStore {

    @NonNull static final String PREF_NAME = "FakeTravelerPrefs.v3";

    @Nullable private static volatile SettingsDataStore INSTANCE;

    @NonNull private final SharedPreferences sp;
    @NonNull private final Map<String, MutableLiveData<Double>> doubleObservers = new HashMap<>();
    @NonNull private final Map<String, MutableLiveData<String>> stringObservers = new HashMap<>();
    @NonNull private final Map<String, MutableLiveData<Boolean>> boolObservers = new HashMap<>();

    private SettingsDataStore(@NonNull Context ctx) {
        this.sp = ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public static SettingsDataStore get(@NonNull Context ctx) {
        SettingsDataStore i = INSTANCE;
        if (i == null) synchronized (SettingsDataStore.class) {
            i = INSTANCE;
            if (i == null) INSTANCE = i = new SettingsDataStore(ctx);
        }
        return i;
    }

    // Doubles are stored as raw long bits so SharedPreferences-only callers can read the
    // same key without precision loss.
    public double getDoubleBlocking(@NonNull String key, double def) {
        if (!sp.contains(key)) return def;
        return Double.longBitsToDouble(sp.getLong(key, Double.doubleToLongBits(def)));
    }

    @Nullable
    public String getStringBlocking(@NonNull String key) {
        return sp.getString(key, null);
    }

    public boolean getBoolBlocking(@NonNull String key, boolean def) {
        return sp.getBoolean(key, def);
    }

    public int getIntBlocking(@NonNull String key, int def) {
        return sp.getInt(key, def);
    }

    public long getLongBlocking(@NonNull String key, long def) {
        return sp.getLong(key, def);
    }

    public void setDoubleBlocking(@NonNull String key, double value) {
        sp.edit().putLong(key, Double.doubleToLongBits(value)).apply();
        emitDouble(key, value);
    }

    public void setStringBlocking(@NonNull String key, @Nullable String value) {
        if (value == null) sp.edit().remove(key).apply();
        else sp.edit().putString(key, value).apply();
        emitString(key, value);
    }

    public void setBoolBlocking(@NonNull String key, boolean value) {
        sp.edit().putBoolean(key, value).apply();
        emitBool(key, value);
    }

    public void setIntBlocking(@NonNull String key, int value) {
        sp.edit().putInt(key, value).apply();
    }

    public void setLongBlocking(@NonNull String key, long value) {
        sp.edit().putLong(key, value).apply();
    }

    /** Returns the same instance for repeated observers of the same key, so listeners
     *  on the same key see the same value stream. */
    @NonNull
    public LiveData<Double> observeDouble(@NonNull String key, double def) {
        synchronized (doubleObservers) {
            MutableLiveData<Double> ld = doubleObservers.get(key);
            if (ld == null) {
                ld = new MutableLiveData<>(getDoubleBlocking(key, def));
                doubleObservers.put(key, ld);
            }
            return ld;
        }
    }

    @NonNull
    public LiveData<String> observeString(@NonNull String key) {
        synchronized (stringObservers) {
            MutableLiveData<String> ld = stringObservers.get(key);
            if (ld == null) {
                ld = new MutableLiveData<>(getStringBlocking(key));
                stringObservers.put(key, ld);
            }
            return ld;
        }
    }

    @NonNull
    public LiveData<Boolean> observeBool(@NonNull String key, boolean def) {
        synchronized (boolObservers) {
            MutableLiveData<Boolean> ld = boolObservers.get(key);
            if (ld == null) {
                ld = new MutableLiveData<>(getBoolBlocking(key, def));
                boolObservers.put(key, ld);
            }
            return ld;
        }
    }

    private void emitDouble(@NonNull String key, double v) {
        synchronized (doubleObservers) {
            MutableLiveData<Double> ld = doubleObservers.get(key);
            if (ld != null) ld.postValue(v);
        }
    }

    private void emitString(@NonNull String key, @Nullable String v) {
        synchronized (stringObservers) {
            MutableLiveData<String> ld = stringObservers.get(key);
            if (ld != null) ld.postValue(v);
        }
    }

    private void emitBool(@NonNull String key, boolean v) {
        synchronized (boolObservers) {
            MutableLiveData<Boolean> ld = boolObservers.get(key);
            if (ld != null) ld.postValue(v);
        }
    }

    @VisibleForTesting
    public void clearForTesting() {
        sp.edit().clear().apply();
    }

    /** Returns an already-completed future. Writes go through SharedPreferences#apply()
     *  which queues asynchronously inside the framework; the in-memory snapshot is
     *  updated synchronously, so read-after-write under InstantTaskExecutorRule needs no
     *  extra drain. Kept for the existing test contract. */
    @VisibleForTesting
    @NonNull
    public Future<?> flushForTesting() {
        final FutureTask<Void> done = new FutureTask<>(() -> null);
        done.run();
        return done;
    }
}
