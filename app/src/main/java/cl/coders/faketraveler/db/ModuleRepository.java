package cl.coders.faketraveler.db;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lazy singleton facade over every module DAO plus a shared background {@link ExecutorService}.
 * Modules pull their DAO from here instead of touching {@link AppDatabase} directly, and route all
 * writes through {@link #io(Runnable)} to keep Room off the main thread. No Hilt in this codebase,
 * so this is the hand-rolled DI seam (mirrors {@link AppDatabase#get(Context)}).
 */
public final class ModuleRepository {

    private static volatile ModuleRepository INSTANCE;

    @NonNull private final AppDatabase db;
    @NonNull private final ExecutorService io;

    private ModuleRepository(@NonNull Context ctx) {
        this.db = AppDatabase.get(ctx.getApplicationContext());
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FT-ModuleIO");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    @NonNull
    public static ModuleRepository get(@NonNull Context ctx) {
        ModuleRepository r = INSTANCE;
        if (r == null) synchronized (ModuleRepository.class) {
            r = INSTANCE;
            if (r == null) INSTANCE = r = new ModuleRepository(ctx);
        }
        return r;
    }

    /** Run a DB operation on the shared background thread. */
    public void io(@NonNull Runnable work) { io.execute(work); }

    @NonNull public MockSessionDao getMockSessionDao()        { return db.mockSessionDao(); }
    @NonNull public RouteDao getRouteDao()                    { return db.routeDao(); }
    @NonNull public GeoFenceDao getGeoFenceDao()              { return db.geoFenceDao(); }
    @NonNull public PermissionSnapshotDao getPermissionSnapshotDao() { return db.permissionSnapshotDao(); }
    @NonNull public PermissionDriftAlertDao getPermissionDriftAlertDao() { return db.permissionDriftAlertDao(); }
    @NonNull public ExifCleanedFileDao getExifCleanedDao()    { return db.exifCleanedFileDao(); }
    @NonNull public PrivacyWipeLogDao getWipeLogDao()         { return db.privacyWipeLogDao(); }
    @NonNull public EvidenceReportDao getEvidenceReportDao()  { return db.evidenceReportDao(); }
    @NonNull public BookmarkDao getBookmarkDao()              { return db.bookmarkDao(); }
}
