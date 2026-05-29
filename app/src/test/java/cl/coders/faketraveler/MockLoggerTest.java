package cl.coders.faketraveler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MockLoggerTest {

    @BeforeEach
    public void setUp() { MockLogger.clear(); }

    @Test public void log_appends_entry() {
        MockLogger.log("event", "msg");
        assertEquals(1, MockLogger.snapshot().size());
        assertEquals("event", MockLogger.snapshot().get(0).level);
        assertEquals("msg", MockLogger.snapshot().get(0).message);
    }

    @org.junit.jupiter.api.Test
    public void ring_buffer_caps_at_1000() {
        for (int i = 0; i < 1500; i++) MockLogger.log("event", "msg" + i);
        assertEquals(1000, MockLogger.snapshot().size());
        assertEquals("msg500", MockLogger.snapshot().get(0).message);
        assertEquals("msg1499", MockLogger.snapshot().get(999).message);
    }

    @org.junit.jupiter.api.Test
    public void export_returns_non_empty_text() {
        MockLogger.log("event", "hello");
        String out = MockLogger.exportText();
        assertTrue(out.contains("hello"));
    }
}
