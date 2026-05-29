package cl.coders.faketraveler.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/** Room access for {@link SavedRouteEntity} and its {@link RouteWaypointEntity} children (Module 2). */
@Dao
public interface RouteDao {

    @Insert
    long insertRoute(SavedRouteEntity route);

    @Update
    void updateRoute(SavedRouteEntity route);

    @Delete
    void deleteRoute(SavedRouteEntity route);

    @Query("SELECT * FROM saved_routes ORDER BY createdAt DESC")
    LiveData<List<SavedRouteEntity>> getAllRoutes();

    @Query("SELECT * FROM saved_routes ORDER BY createdAt DESC")
    List<SavedRouteEntity> getAllRoutesSync();

    @Query("SELECT * FROM saved_routes WHERE id = :routeId")
    SavedRouteEntity getRoute(long routeId);

    @Insert
    long insertWaypoint(RouteWaypointEntity waypoint);

    @Insert
    void insertWaypoints(List<RouteWaypointEntity> waypoints);

    @Query("SELECT * FROM route_waypoints WHERE routeId = :routeId ORDER BY sequence")
    List<RouteWaypointEntity> getWaypointsForRoute(long routeId);

    @Query("DELETE FROM route_waypoints WHERE routeId = :routeId")
    void deleteWaypointsForRoute(long routeId);

    @Query("DELETE FROM saved_routes")
    void deleteAllRoutes();
}
