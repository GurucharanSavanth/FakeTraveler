package cl.coders.faketraveler;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Module 8: runs a potentially large {@link EvidenceExporter} export off the main thread. The UI uses
 * a direct IO call for small exports; this worker exists for big/full-database exports enqueued via
 * {@link #enqueue}. Options are passed through {@link Data}.
 */
public class EvidenceExportWorker extends Worker {

    private static final String TAG = "EvidenceExportWorker";
    private static final String UNIQUE_WORK = "cl.coders.faketraveler.evidence_export";

    public static final String KEY_FORMAT = "format";
    public static final String KEY_SESSIONS = "sessions";
    public static final String KEY_ROUTES = "routes";
    public static final String KEY_GEOFENCES = "geofences";
    public static final String KEY_PERMISSIONS = "permissions";
    public static final String KEY_EXIF = "exif";
    public static final String KEY_WIPES = "wipes";

    public EvidenceExportWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override
    public Result doWork() {
        final Context ctx = getApplicationContext();
        if (!FeatureFlag.EVIDENCE_EXPORT.isEnabled(ctx)) return Result.success();
        final Data in = getInputData();
        final EvidenceExporter.Options o = new EvidenceExporter.Options();
        o.format = in.getString(KEY_FORMAT) != null ? in.getString(KEY_FORMAT) : "json";
        o.sessions = in.getBoolean(KEY_SESSIONS, true);
        o.routes = in.getBoolean(KEY_ROUTES, true);
        o.geofences = in.getBoolean(KEY_GEOFENCES, true);
        o.permissions = in.getBoolean(KEY_PERMISSIONS, true);
        o.exif = in.getBoolean(KEY_EXIF, true);
        o.wipes = in.getBoolean(KEY_WIPES, true);
        try {
            new EvidenceExporter(ctx).export(o);
            return Result.success();
        } catch (Throwable t) {
            Log.e(TAG, "evidence export failed", t);
            return Result.retry();
        }
    }

    public static void enqueue(@NonNull Context ctx, @NonNull Data data) {
        final OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(EvidenceExportWorker.class)
                .setInputData(data)
                .build();
        WorkManager.getInstance(ctx).enqueue(req);
    }
}
