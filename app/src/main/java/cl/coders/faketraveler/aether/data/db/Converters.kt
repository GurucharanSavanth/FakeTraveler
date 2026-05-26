package cl.coders.faketraveler.aether.data.db

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList()
        else value.split(",")

    @TypeConverter
    fun toStringList(list: List<String>): String =
        list.joinToString(",")
}
