package cl.coders.faketraveler.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** Room access for {@link ExifCleanedFileEntity} (Module 6). Keyed by file path, so upserts replace. */
@Dao
public interface ExifCleanedFileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ExifCleanedFileEntity file);

    @Query("SELECT * FROM exif_cleaned_files ORDER BY cleanedAt DESC")
    LiveData<List<ExifCleanedFileEntity>> getAllCleanedFiles();

    @Query("SELECT * FROM exif_cleaned_files ORDER BY cleanedAt DESC")
    List<ExifCleanedFileEntity> getAllCleanedFilesSync();

    @Query("SELECT * FROM exif_cleaned_files WHERE hadGpsData = 1 ORDER BY cleanedAt DESC")
    List<ExifCleanedFileEntity> getFilesWithGps();

    @Query("DELETE FROM exif_cleaned_files WHERE filePath = :path")
    void deleteByPath(String path);

    @Query("DELETE FROM exif_cleaned_files")
    void deleteAll();
}
