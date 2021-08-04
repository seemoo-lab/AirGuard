package de.seemoo.at_tracking_detection.util.converter

import androidx.room.TypeConverter
import java.time.LocalDateTime

class DateTimeConverter {
    @TypeConverter
    fun toDateTime(dateTimeString: String?): LocalDateTime? {
        return dateTimeString?.let { LocalDateTime.parse(it) }
    }

    @TypeConverter
    fun fromDateTime(dateTime: LocalDateTime?): String? {
        return dateTime?.toString()
    }
}