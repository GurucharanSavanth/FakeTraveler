package cl.coders.faketraveler.cooldown;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Duration;

public class CooldownCalculatorTest {

    @Test public void zero_when_under_1km() {
        Duration d = CooldownCalculator.compute(0.0, 0.0, 0.001, 0.0);   // ~111 m
        assertEquals(Duration.ZERO, d);
    }

    @Test public void thirty_seconds_when_1_to_2km() {
        Duration d = CooldownCalculator.compute(0.0, 0.0, 0.0135, 0.0);  // ~1.5 km
        assertEquals(Duration.ofSeconds(30), d);
    }

    @Test public void one_minute_when_2_to_5km() {
        Duration d = CooldownCalculator.compute(0.0, 0.0, 0.03, 0.0);    // ~3.3 km
        assertEquals(Duration.ofMinutes(1), d);
    }

    @Test public void ten_minutes_when_25_to_100km() {
        Duration d = CooldownCalculator.compute(0.0, 0.0, 0.5, 0.0);     // ~55 km
        assertEquals(Duration.ofMinutes(10), d);
    }

    @Test public void sixty_minutes_when_above_1000km() {
        Duration d = CooldownCalculator.compute(0.0, 0.0, 20.0, 0.0);    // ~2 222 km
        assertEquals(Duration.ofMinutes(60), d);
    }
}
