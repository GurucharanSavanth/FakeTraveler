package cl.coders.faketraveler.cooldown;

import androidx.annotation.NonNull;

import java.time.Duration;

import cl.coders.faketraveler.joystick.JoystickEngine;

/**
 * Maps a teleport distance to the wait imposed before the user can apply the new mock.
 * The table approximates published rate-limits for popular detection-aware target apps
 * (e.g. location-gated games); see the design doc §5.2.4 for the source.
 */
public final class CooldownCalculator {

    private CooldownCalculator() {}

    @NonNull
    public static Duration compute(double lat1, double lng1, double lat2, double lng2) {
        if (Double.isNaN(lat1) || Double.isNaN(lng1) || Double.isNaN(lat2) || Double.isNaN(lng2)) {
            return Duration.ZERO;
        }
        final double km = JoystickEngine.haversineKm(lat1, lng1, lat2, lng2);
        if (Double.isNaN(km) || Double.isInfinite(km)) return Duration.ZERO;
        if (km < 1)      return Duration.ZERO;
        if (km < 2)      return Duration.ofSeconds(30);
        if (km < 5)      return Duration.ofMinutes(1);
        if (km < 10)     return Duration.ofMinutes(3);
        if (km < 25)     return Duration.ofMinutes(5);
        if (km < 100)    return Duration.ofMinutes(10);
        if (km < 250)    return Duration.ofMinutes(20);
        if (km < 500)    return Duration.ofMinutes(30);
        if (km < 750)    return Duration.ofMinutes(40);
        if (km < 1000)   return Duration.ofMinutes(50);
        return Duration.ofMinutes(60);
    }
}
