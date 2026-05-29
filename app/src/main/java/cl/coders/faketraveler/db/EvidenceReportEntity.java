package cl.coders.faketraveler.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Metadata for a generated audit/evidence report file (Module 8). */
@Entity(tableName = "evidence_reports")
public class EvidenceReportEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long generatedAt;
    /** "json", "csv", or "pdf". */
    public String reportType;
    /** App-private or shared-storage path of the written file. */
    public String filePath;
    public long fileSizeBytes;
    public String checksumSha256;
    /** Optional session filter; 0 = all sessions. */
    public long sessionId;
    public long dateFrom;
    public long dateTo;
    public boolean includesSessions;
    public boolean includesRoutes;
    public boolean includesGeoFences;
    /** Retained for schema stability; Module 4 (network) was dropped. */
    public boolean includesNetwork;
    public boolean includesPermissions;
    public boolean includesExif;
    public boolean includesWipes;
}
