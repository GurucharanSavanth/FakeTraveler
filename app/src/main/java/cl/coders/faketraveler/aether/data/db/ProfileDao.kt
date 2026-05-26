package cl.coders.faketraveler.aether.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE targetApps LIKE '%' || :packageName || '%'")
    suspend fun findByTargetApp(packageName: String): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Update
    suspend fun update(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(profiles: List<ProfileEntity>) {
        deleteAll()
        profiles.forEach { upsert(it) }
    }
}
