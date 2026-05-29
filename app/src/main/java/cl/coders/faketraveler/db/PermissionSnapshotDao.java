package cl.coders.faketraveler.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/** Room access for {@link PermissionSnapshotEntity} (Module 5). */
@Dao
public interface PermissionSnapshotDao {

    @Insert
    void insert(PermissionSnapshotEntity snapshot);

    @Insert
    void insertAll(List<PermissionSnapshotEntity> snapshots);

    @Query("SELECT MAX(timestamp) FROM permission_snapshots")
    long getLatestTimestamp();

    @Query("SELECT * FROM permission_snapshots WHERE packageName = :packageName ORDER BY timestamp DESC")
    List<PermissionSnapshotEntity> getSnapshotsForApp(String packageName);

    @Query("SELECT * FROM permission_snapshots WHERE timestamp = :timestamp")
    List<PermissionSnapshotEntity> getSnapshotsAt(long timestamp);

    @Query("DELETE FROM permission_snapshots")
    void deleteAll();
}
