package cl.coders.faketraveler.aether.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profiles",
    indices = [Index(value = ["name"], name = "idx_profile_name")]
)
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val movementMode: String,
    val targetApps: String,
    val accuracyJitter: Boolean,
    val updateIntervalMs: Long,
    val createdAt: Long
)
