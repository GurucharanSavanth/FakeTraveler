package cl.coders.faketraveler;

import static org.junit.Assert.assertEquals;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import org.junit.Rule;
import org.junit.jupiter.api.Test;

public class MockViewModelTest {

    @Rule public final InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    @Test public void initial_state_is_not_mocked() {
        MockViewModel vm = new MockViewModel(new SavedStateHandle());
        assertEquals(MockState.NOT_MOCKED, vm.mockState().getValue());
    }

    @Test public void saved_state_survives_recreation() {
        SavedStateHandle h = new SavedStateHandle();
        MockViewModel vm = new MockViewModel(h);
        vm.setLatLng(12.34, 56.78);

        MockViewModel vm2 = new MockViewModel(h);
        assertEquals(12.34, vm2.lat(), 1e-9);
        assertEquals(56.78, vm2.lng(), 1e-9);
    }

    @org.junit.jupiter.api.Test
    public void mock_state_publishes_to_live_data() {
        MockViewModel vm = new MockViewModel(new SavedStateHandle());
        vm.updateMockState(MockState.MOCKED);
        assertEquals(MockState.MOCKED, vm.mockState().getValue());
    }

    @Test public void mock_state_survives_handle_roundtrip() {
        SavedStateHandle h = new SavedStateHandle();
        MockViewModel vm = new MockViewModel(h);
        vm.updateMockState(MockState.MOCKED);

        MockViewModel vm2 = new MockViewModel(h);
        assertEquals(MockState.MOCKED, vm2.mockState().getValue());
    }
}
