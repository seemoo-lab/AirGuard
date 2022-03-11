package de.seemoo.at_tracking_detection.database

import androidx.room.TypeConverter
import java.time.LocalDateTime

class Converters {

    @TypeConverter
    fun fromStringToList(value: String): List<String> {
        return value.split(";")
    }

    @TypeConverter
    fun toStringFromList(list: List<String>?): String {
        return list?.joinToString(";") ?: ""
    }
}