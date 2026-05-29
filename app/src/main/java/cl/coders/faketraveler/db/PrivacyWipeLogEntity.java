package cl.coders.faketraveler.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Audit record of a privacy-wipe run (Module 7). */
@Entity(tableName = "privacy_wipe_logs")
public class PrivacyWipeLogEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long wipedAt;
    /** 0=app_data 1=session_history 2=exif_backups 3=network_obs 4=permission_snapshots 5=geofence_events 6=full. */
    public int wipeType;
    public boolean success;
    /** JSON array of {target, status, bytesRemoved}; null on catastrophic failure. */
    public String detailsJson;
}
