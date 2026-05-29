package cl.coders.faketraveler.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room database singleton.
 *
 * <p>v2 held a single {@link BookmarkEntity} table. v3 adds the P6–P8 feature-module tables
 * (mock sessions + route points, saved routes + waypoints, geofences + events, permission
 * snapshots + drift alerts, EXIF clean log, privacy-wipe log, evidence reports). Module 4's
 * network-observation table was dropped before implementation, so it is absent here.
 *
 * <p>The v1->v2 migration renames the legacy {@code favorites} table to {@code bookmarks}. The
 * v2->v3 migration only CREATEs new tables, so existing bookmark rows are untouched. No
 * destructive fallback is wired — adding one would mask migration regressions.
 *
 * <p>Note: Room overwrites the hand-authored {@code schemas/.../3.json} identityHash on the first
 * real build; tests read entity classes directly so the placeholder does not block them.
 */
@Database(entities = {
        BookmarkEntity.class,
        MockSessionEntity.class, RoutePointEntity.class,
        SavedRouteEntity.class, RouteWaypointEntity.class,
        GeoFenceEntity.class, GeoFenceEventEntity.class,
        PermissionSnapshotEntity.class, PermissionDriftAlertEntity.class,
        ExifCleanedFileEntity.class,
        PrivacyWipeLogEntity.class,
        EvidenceReportEntity.class
}, version = 3, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract BookmarkDao bookmarkDao();
    public abstract MockSessionDao mockSessionDao();
    public abstract RouteDao routeDao();
    public abstract GeoFenceDao geoFenceDao();
    public abstract PermissionSnapshotDao permissionSnapshotDao();
    public abstract PermissionDriftAlertDao permissionDriftAlertDao();
    public abstract ExifCleanedFileDao exifCleanedFileDao();
    public abstract PrivacyWipeLogDao privacyWipeLogDao();
    public abstract EvidenceReportDao evidenceReportDao();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Cross-version-safe rename: copy rows into the new table, then drop the old one.
            db.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`lat` REAL NOT NULL, " +
                    "`lng` REAL NOT NULL, " +
                    "`zoom` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)");
            db.execSQL("INSERT INTO `bookmarks` (`id`, `name`, `lat`, `lng`, `zoom`, `createdAt`) " +
                    "SELECT `id`, `name`, `lat`, `lng`, `zoom`, `createdAt` FROM `favorites`");
            db.execSQL("DROP TABLE `favorites`");
        }
    };

    /**
     * v2->v3: additive only. Each CREATE mirrors the Room-generated schema exactly (column order,
     * NOT NULL, FK clauses, index names) so {@code MigrationTestHelper.validateMigration} passes.
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `mock_sessions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, " +
                    "`startLat` REAL NOT NULL, `startLng` REAL NOT NULL, " +
                    "`endLat` REAL NOT NULL, `endLng` REAL NOT NULL, " +
                    "`mockCount` INTEGER NOT NULL, `mockFrequencyMs` INTEGER NOT NULL, " +
                    "`completed` INTEGER NOT NULL, `sessionLabel` TEXT)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `route_points` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sessionId` INTEGER NOT NULL, `sequence` INTEGER NOT NULL, " +
                    "`lat` REAL NOT NULL, `lng` REAL NOT NULL, `altitude` REAL NOT NULL, " +
                    "`accuracy` REAL NOT NULL, `timestampOffsetMs` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`sessionId`) REFERENCES `mock_sessions`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_points_sessionId` " +
                    "ON `route_points` (`sessionId`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `saved_routes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `description` TEXT, `createdAt` INTEGER NOT NULL, " +
                    "`pointCount` INTEGER NOT NULL, `totalDistanceMeters` REAL NOT NULL, " +
                    "`estimatedDurationSeconds` INTEGER NOT NULL)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `route_waypoints` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`routeId` INTEGER NOT NULL, `sequence` INTEGER NOT NULL, " +
                    "`lat` REAL NOT NULL, `lng` REAL NOT NULL, `speedKmh` REAL NOT NULL, " +
                    "`dwellMs` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`routeId`) REFERENCES `saved_routes`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_waypoints_routeId` " +
                    "ON `route_waypoints` (`routeId`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `geofences` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `centerLat` REAL NOT NULL, `centerLng` REAL NOT NULL, " +
                    "`radiusMeters` REAL NOT NULL, `polygonJson` TEXT, `type` INTEGER NOT NULL, " +
                    "`monitorEntry` INTEGER NOT NULL, `monitorExit` INTEGER NOT NULL, " +
                    "`monitorDwell` INTEGER NOT NULL, `dwellMs` INTEGER NOT NULL, " +
                    "`active` INTEGER NOT NULL)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `geofence_events` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`geofenceId` INTEGER NOT NULL, `eventType` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, `triggeredLat` REAL NOT NULL, " +
                    "`triggeredLng` REAL NOT NULL, `sessionId` INTEGER NOT NULL)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `permission_snapshots` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, `packageName` TEXT NOT NULL, " +
                    "`appName` TEXT, `permission` TEXT NOT NULL, `status` INTEGER NOT NULL, " +
                    "`isNew` INTEGER NOT NULL, `isDangerous` INTEGER NOT NULL)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `permission_drift_alerts` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, `packageName` TEXT NOT NULL, " +
                    "`appName` TEXT, `permission` TEXT NOT NULL, `driftType` INTEGER NOT NULL, " +
                    "`severity` INTEGER NOT NULL, `acknowledged` INTEGER NOT NULL)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `exif_cleaned_files` (" +
                    "`filePath` TEXT NOT NULL, `cleanedAt` INTEGER NOT NULL, " +
                    "`hadGpsData` INTEGER NOT NULL, `originalLat` REAL NOT NULL, " +
                    "`originalLng` REAL NOT NULL, `originalAltitude` REAL NOT NULL, " +
                    "`backupCreated` INTEGER NOT NULL, `backupPath` TEXT, " +
                    "PRIMARY KEY(`filePath`))");

            db.execSQL("CREATE TABLE IF NOT EXISTS `privacy_wipe_logs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`wipedAt` INTEGER NOT NULL, `wipeType` INTEGER NOT NULL, " +
                    "`success` INTEGER NOT NULL, `detailsJson` TEXT)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `evidence_reports` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`generatedAt` INTEGER NOT NULL, `reportType` TEXT, `filePath` TEXT, " +
                    "`fileSizeBytes` INTEGER NOT NULL, `checksumSha256` TEXT, " +
                    "`sessionId` INTEGER NOT NULL, `dateFrom` INTEGER NOT NULL, " +
                    "`dateTo` INTEGER NOT NULL, `includesSessions` INTEGER NOT NULL, " +
                    "`includesRoutes` INTEGER NOT NULL, `includesGeoFences` INTEGER NOT NULL, " +
                    "`includesNetwork` INTEGER NOT NULL, `includesPermissions` INTEGER NOT NULL, " +
                    "`includesExif` INTEGER NOT NULL, `includesWipes` INTEGER NOT NULL)");
        }
    };

    @NonNull
    public static AppDatabase get(@NonNull Context ctx) {
        AppDatabase db = INSTANCE;
        if (db == null) synchronized (AppDatabase.class) {
            db = INSTANCE;
            if (db == null) INSTANCE = db = Room.databaseBuilder(
                    ctx.getApplicationContext(), AppDatabase.class, "faketraveler.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build();
        }
        return db;
    }
}
