package cl.coders.faketraveler;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import cl.coders.faketraveler.detection.PrivacyExposureScanner;

/**
 * Fires ONE advisory notification after a mock is applied, only if {@link FeatureFlag#PRIVACY_GUARD}
 * is on and the device is at HIGH exposure. Advisory only — it never alters the mock lifecycle.
 */
public final class PrivacyGuardNotifier {

    private static final Executor BG = Executors.newSingleThreadExecutor();

    private PrivacyGuardNotifier() { throw new UnsupportedOperationException(); }

    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS SecurityException caught below
    public static void maybeNotify(@NonNull Context ctx) {
        final Context app = ctx.getApplicationContext();
        if (!FeatureFlag.PRIVACY_GUARD.isEnabled(app)) return;
        BG.execute(() -> {
            try {
                final PrivacyExposureScanner.Report r = PrivacyExposureScanner.run(app);
                if (r.risk != PrivacyExposureScanner.Risk.HIGH) return;
                NotificationManagerCompat.from(app).notify(
                        NotificationFactory.PRIVACY_NOTIFICATION_ID,
                        NotificationFactory.buildPrivacyExposure(app));
            } catch (Throwable ignored) {
                // POST_NOTIFICATIONS not granted (API 33+) or transient failure — best effort.
            }
        });
    }
}
