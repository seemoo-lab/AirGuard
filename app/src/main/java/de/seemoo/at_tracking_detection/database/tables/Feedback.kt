package de.seemoo.at_tracking_detection.database.tables

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import de.seemoo.at_tracking_detection.util.converter.DateTimeConverter

@Entity(tableName = "feedback")
@TypeConverters(DateTimeConverter::class)
data class Feedback(
    @PrimaryKey(autoGenerate = true) val feedbackId: Int,
    @ColumnInfo(name = "notificationId") val notificationId: Int,
    @ColumnInfo(name = "location") val location: String?,
) {
    constructor(
        notificationId: Int,
        location: String?
    ) : this(
        0,
        notificationId,
        location
    )
}