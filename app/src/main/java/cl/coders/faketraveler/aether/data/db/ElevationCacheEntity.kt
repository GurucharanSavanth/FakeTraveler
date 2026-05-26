package cl.coders.faketraveler.aether.data.db

import androidx.room.Entity

@Entity(
    tableName = "elevation_cache",
    primaryKeys = ["latIdx", "lonIdx"]
)
data class ElevationCacheEntity(
    val latIdx: Int,
    val lonIdx: Int,
    val elevation: Double,
    val fetchedAt: Long
)
