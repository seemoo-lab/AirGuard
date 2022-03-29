package de.seemoo.at_tracking_detection.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import de.seemoo.at_tracking_detection.util.converter.DateTimeConverter
import java.time.LocalDateTime

@Entity(tableName = "scan")
@TypeConverters(DateTimeConverter::class)
data class Scan(
    @PrimaryKey(autoGenerate = true) val scanId: Int,
    /*Date when the scan has finished*/
    @ColumnInfo(name = "date") val date: LocalDateTime,
    /*Number of devices found during the scan*/
    @ColumnInfo(name = "noDevicesFound") val noDevicesFound: Int,
    /*Duration in seconds of the scan*/
    @ColumnInfo(name = "duration") val duration: Int,
    /*`True` if the scan is manually started*/
    @ColumnInfo(name = "isManual") val isManual: Boolean,
    /*Android Scan mode used*/
    @ColumnInfo(name = "scanMode") val scanMode: Int
) {
    constructor(
        date: LocalDateTime,
        noDevicesFound: Int,
        duration: Int,
        isManual: Boolean,
        scanMode: Int
    ): this(
        scanId = 0,
        date,
        noDevicesFound,
        duration,
        isManual,
        scanMode
    )

}
