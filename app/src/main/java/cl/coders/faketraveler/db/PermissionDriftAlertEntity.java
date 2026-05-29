package cl.coders.faketraveler.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A flagged permission change between two snapshots (Module 5). */
@Entity(tableName = "permission_drift_alerts")
public class PermissionDriftAlertEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;

    @NonNull
    public String packageName = "";

    public String appName;

    @NonNull
    public String permission = "";

    /** 0 = added, 1 = removed, 2 = granted_after_denial, 3 = escalated_to_dangerous. */
    public int driftType;
    /** 0 = info, 1 = warning, 2 = critical. */
    public int severity;
    public boolean acknowledged;
}
