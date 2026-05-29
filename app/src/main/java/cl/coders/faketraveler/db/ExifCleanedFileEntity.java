package cl.coders.faketraveler.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Record of a photo whose GPS EXIF tags were stripped (Module 6). Keyed by absolute path. */
@Entity(tableName = "exif_cleaned_files")
public class ExifCleanedFileEntity {

    @PrimaryKey
    @NonNull
    public String filePath = "";

    public long cleanedAt;
    public boolean hadGpsData;
    public double originalLat;
    public double originalLng;
    public double originalAltitude;
    public boolean backupCreated;
    public String backupPath;
}
