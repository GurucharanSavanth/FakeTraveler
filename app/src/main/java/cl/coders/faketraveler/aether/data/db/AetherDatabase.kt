package cl.coders.faketraveler.aether.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, ElevationCacheEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AetherDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun elevationCacheDao(): ElevationCacheDao

    companion object {
        fun create(context: Context): AetherDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AetherDatabase::class.java,
                "aether_database"
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
