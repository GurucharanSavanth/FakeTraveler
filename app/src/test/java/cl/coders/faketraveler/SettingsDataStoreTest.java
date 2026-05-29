package cl.coders.faketraveler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SettingsDataStoreTest {

    @Rule public final InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private SettingsDataStore ds;

    @BeforeEach
    public void setUp() {
        ds = SettingsDataStore.get(ApplicationProvider.getApplicationContext());
        ds.clearForTesting();
    }

    @AfterEach
    public void tearDown() {
        ds.clearForTesting();
    }

    @Test public void absent_key_returns_default() {
        assertEquals(0.0, ds.getDoubleBlocking("lat", 0.0), 0.0);
        assertNull(ds.getStringBlocking("routeData"));
    }

    @org.junit.jupiter.api.Test
    public void write_then_read_roundtrip() {
        ds.setDoubleBlocking("lat", 12.34);
        ds.setStringBlocking("routeData", "{\"pts\":[]}");
        ds.setBoolBlocking("flag", true);
        assertEquals(12.34, ds.getDoubleBlocking("lat", 0.0), 1e-9);
        assertEquals("{\"pts\":[]}", ds.getStringBlocking("routeData"));
        assertEquals(true, ds.getBoolBlocking("flag", false));
    }

    @Test public void livedata_emits_on_change() throws Exception {
        final AtomicReference<Double> seen = new AtomicReference<>();
        ds.observeDouble("lat", 0.0).observeForever(seen::set);
        ds.setDoubleBlocking("lat", 99.0);
        ds.flushForTesting().get();
        assertEquals(99.0, seen.get(), 0.0);
    }
}
