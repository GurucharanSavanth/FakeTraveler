package cl.coders.faketraveler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

/**
 * Holds the UI's view of mock state. Survives Activity rotation through the ViewModelStore
 * and survives process death through {@link SavedStateHandle}.
 *
 * <p>The service owns the mock lifecycle, not this ViewModel. {@link #onCleared()} MUST
 * NOT stop the mock: a rotation tears the ViewModel down even though the user expects the
 * mock to continue (V43).
 */
public class MockViewModel extends ViewModel {

    static final String KEY_LAT = "lat";
    static final String KEY_LNG = "lng";
    static final String KEY_MOCK_STATE = "mockState";

    @NonNull private final SavedStateHandle handle;
    @NonNull private final MutableLiveData<MockState> mockState;

    public MockViewModel(@NonNull SavedStateHandle handle) {
        this.handle = handle;
        final String restored = handle.get(KEY_MOCK_STATE);
        MockState start = MockState.NOT_MOCKED;
        if (restored != null) {
            try {
                start = MockState.valueOf(restored);
            } catch (IllegalArgumentException iae) {
                MockLogger.log("vm_state_parse", "unknown MockState=" + restored + " → NOT_MOCKED");
            }
        }
        this.mockState = new MutableLiveData<>(start);
    }

    @NonNull
    public LiveData<MockState> mockState() { return mockState; }

    public void updateMockState(@NonNull MockState s) {
        mockState.setValue(s);
        handle.set(KEY_MOCK_STATE, s.name());
    }

    public double lat() {
        Double v = handle.get(KEY_LAT);
        return v == null ? Double.NaN : v;
    }

    public double lng() {
        Double v = handle.get(KEY_LNG);
        return v == null ? Double.NaN : v;
    }

    public void setLatLng(double lat, double lng) {
        handle.set(KEY_LAT, lat);
        handle.set(KEY_LNG, lng);
    }

    @Nullable
    public <T> T get(@NonNull String key) { return handle.get(key); }

    public <T> void put(@NonNull String key, @Nullable T value) { handle.set(key, value); }
}
