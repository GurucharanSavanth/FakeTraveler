package cl.coders.faketraveler.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/** Room access for {@link MockSessionEntity} and its child {@link RoutePointEntity} (Module 1). */
@Dao
public interface MockSessionDao {

    @Insert
    long insert(MockSessionEntity session);

    @Update
    void update(MockSessionEntity session);

    @Delete
    void deleteSession(MockSessionEntity session);

    @Query("SELECT * FROM mock_sessions ORDER BY startTime DESC")
    LiveData<List<MockSessionEntity>> getAllSessions();

    @Query("SELECT * FROM mock_sessions ORDER BY startTime DESC")
    List<MockSessionEntity> getAllSessionsSync();

    @Query("SELECT * FROM mock_sessions WHERE id = :sessionId")
    MockSessionEntity getSession(long sessionId);

    @Insert
    long insertPoint(RoutePointEntity point);

    @Insert
    void insertPoints(List<RoutePointEntity> points);

    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId ORDER BY sequence")
    List<RoutePointEntity> getPointsForSession(long sessionId);

    @Query("DELETE FROM mock_sessions")
    void deleteAllSessions();
}
