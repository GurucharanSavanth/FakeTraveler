package cl.coders.faketraveler;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

import com.google.android.material.color.DynamicColors;

/**
 * Process-wide setup.
 *
 * <ul>
 *   <li>Applies Material 3 dynamic color to every activity.</li>
 *   <li>Provides {@link WorkManager} configuration so the orchestration pool can be tuned
 *       independently of the live mock-location service thread.</li>
 *   <li>Runs the v2 SharedPreferences migration on cold start — moved here from
 *       {@code MainActivity.onCreate} so a boot worker that fires before any activity is
 *       launched still reads migrated keys.</li>
 * </ul>
 */
public class FakeTravelerApplication extends Application implements Configuration.Provider {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        try {
            SharedPrefsUtil.migrateToV2(this);
        } catch (Throwable t) {
            // V11: migration must never crash the process.
            Log.e("FakeTravelerApplication", "migrateToV2 threw", t);
        }
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .build();
    }
}
