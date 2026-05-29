package cl.coders.faketraveler.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/** Room access for {@link GeoFenceEntity} and {@link GeoFenceEventEntity} (Module 3). */
@Dao
public interface GeoFenceDao {

    @Insert
    long insert(GeoFenceEntity fence);

    @Update
    void update(GeoFenceEntity fence);

    @Delete
    void delete(GeoFenceEntity fence);

    @Query("SELECT * FROM geofences ORDER BY name")
    LiveData<List<GeoFenceEntity>> getAllFences();

    @Query("SELECT * FROM geofences WHERE active = 1")
    List<GeoFenceEntity> getActiveGeoFences();

    @Insert
    long insertEvent(GeoFenceEventEntity event);

    @Query("SELECT * FROM geofence_events ORDER BY timestamp DESC")
    LiveData<List<GeoFenceEventEntity>> getAllEvents();

    @Query("SELECT * FROM geofence_events ORDER BY timestamp DESC")
    List<GeoFenceEventEntity> getAllEventsSync();

    @Query("SELECT * FROM geofences ORDER BY name")
    List<GeoFenceEntity> getAllFencesSync();

    @Query("SELECT * FROM geofence_events WHERE geofenceId = :geofenceId ORDER BY timestamp DESC")
    List<GeoFenceEventEntity> getEventsForFence(long geofenceId);

    @Query("SELECT * FROM geofence_events WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    List<GeoFenceEventEntity> getEventsForSession(long sessionId);

    @Query("DELETE FROM geofence_events")
    void deleteAllEvents();
}
