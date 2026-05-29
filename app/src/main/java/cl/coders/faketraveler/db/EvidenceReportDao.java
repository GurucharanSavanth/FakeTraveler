package cl.coders.faketraveler.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/** Room access for {@link EvidenceReportEntity} (Module 8). */
@Dao
public interface EvidenceReportDao {

    @Insert
    long insert(EvidenceReportEntity report);

    @Delete
    void delete(EvidenceReportEntity report);

    @Query("SELECT * FROM evidence_reports ORDER BY generatedAt DESC")
    LiveData<List<EvidenceReportEntity>> getAllReports();

    @Query("SELECT * FROM evidence_reports ORDER BY generatedAt DESC")
    List<EvidenceReportEntity> getAllReportsSync();

    @Query("SELECT * FROM evidence_reports WHERE sessionId = :sessionId ORDER BY generatedAt DESC")
    List<EvidenceReportEntity> getReportsForSession(long sessionId);

    @Query("DELETE FROM evidence_reports")
    void deleteAll();
}
