package cl.coders.faketraveler.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/** Room access for {@link PermissionDriftAlertEntity} (Module 5). */
@Dao
public interface PermissionDriftAlertDao {

    @Insert
    void insert(PermissionDriftAlertEntity alert);

    @Insert
    void insertAll(List<PermissionDriftAlertEntity> alerts);

    @Query("SELECT * FROM permission_drift_alerts ORDER BY timestamp DESC")
    LiveData<List<PermissionDriftAlertEntity>> getAllAlerts();

    @Query("SELECT * FROM permission_drift_alerts WHERE acknowledged = 0 ORDER BY severity DESC, timestamp DESC")
    List<PermissionDriftAlertEntity> getUnacknowledgedAlerts();

    @Query("SELECT * FROM permission_drift_alerts WHERE packageName = :packageName ORDER BY timestamp DESC")
    List<PermissionDriftAlertEntity> getAlertsForApp(String packageName);

    @Query("UPDATE permission_drift_alerts SET acknowledged = 1 WHERE packageName = :packageName")
    void acknowledgeForApp(String packageName);

    @Query("UPDATE permission_drift_alerts SET acknowledged = 1")
    void acknowledgeAll();

    @Query("DELETE FROM permission_drift_alerts")
    void deleteAll();
}
