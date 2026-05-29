package cl.coders.faketraveler.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/** Room access for {@link PrivacyWipeLogEntity} (Module 7). */
@Dao
public interface PrivacyWipeLogDao {

    @Insert
    long insert(PrivacyWipeLogEntity log);

    @Query("SELECT * FROM privacy_wipe_logs ORDER BY wipedAt DESC")
    LiveData<List<PrivacyWipeLogEntity>> getAllWipes();

    @Query("SELECT * FROM privacy_wipe_logs ORDER BY wipedAt DESC")
    List<PrivacyWipeLogEntity> getAllWipesSync();

    @Query("SELECT * FROM privacy_wipe_logs ORDER BY wipedAt DESC LIMIT 1")
    PrivacyWipeLogEntity getLastWipe();

    @Query("DELETE FROM privacy_wipe_logs")
    void deleteAll();
}
