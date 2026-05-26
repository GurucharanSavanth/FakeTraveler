package cl.coders.faketraveler.aether.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ElevationCacheDao {

    @Query("SELECT * FROM elevation_cache WHERE latIdx = :latIdx AND lonIdx = :lonIdx")
    suspend fun get(latIdx: Int, lonIdx: Int): ElevationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ElevationCacheEntity)

    @Query("DELETE FROM elevation_cache WHERE fetchedAt < :cutoffMs")
    suspend fun evictOlderThan(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM elevation_cache")
    suspend fun count(): Int
}
