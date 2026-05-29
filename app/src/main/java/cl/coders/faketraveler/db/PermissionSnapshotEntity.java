package cl.coders.faketraveler.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One app/permission pair captured during a permission scan (Module 5). */
@Entity(tableName = "permission_snapshots")
public class PermissionSnapshotEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;

    @NonNull
    public String packageName = "";

    public String appName;

    @NonNull
    public String permission = "";

    /** 0 = denied, 1 = granted, 2 = not_requested. */
    public int status;
    /** True if this permission was absent in the previous snapshot. */
    public boolean isNew;
    /** True if the platform classes this permission as dangerous. */
    public boolean isDangerous;
}
