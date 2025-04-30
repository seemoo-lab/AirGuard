package de.seemoo.at_tracking_detection.database.models

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import de.seemoo.at_tracking_detection.util.converter.DateTimeConverter
import java.time.LocalDateTime

@Keep
@Entity(tableName = "notification")
@TypeConverters(DateTimeConverter::class)
data class Notification(
    @PrimaryKey(autoGenerate = true) val notificationId: Int,
    @ColumnInfo(name = "deviceAddress") var deviceAddress: String,
    @ColumnInfo(name = "falseAlarm") val falseAlarm: Boolean,
    @ColumnInfo(name = "dismissed") val dismissed: Boolean?,
    @ColumnInfo(name = "clicked") val clicked: Boolean?,
    @ColumnInfo(name = "createdAt") val createdAt: LocalDateTime,
    @ColumnInfo(name = "sensitivity", defaultValue = "-1") val sensitivity: Int // -1 = unknown, 0 = low, 1 = medium, 2 = high
) {
    constructor(
        deviceAddress: String,
        falseAlarm: Boolean,
        createdAt: LocalDateTime,
        sensitivity: Int
    ) : this(0, deviceAddress, falseAlarm, false, false, createdAt, sensitivity)
}