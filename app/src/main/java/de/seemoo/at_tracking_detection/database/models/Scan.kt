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
    @ColumnInfo(name = "endDate") var endDate: LocalDateTime?,
    /*Number of devices found during the scan*/
    @ColumnInfo(name = "noDevicesFound") var noDevicesFound: Int?,
    /*Duration in seconds of the scan*/
    @ColumnInfo(name = "duration") var duration: Int?,
    /*`True` if the scan is manually started*/
    @ColumnInfo(name = "isManual") val isManual: Boolean,
    /*Android Scan mode used*/
    @ColumnInfo(name = "scanMode") val scanMode: Int,
    @ColumnInfo(name = "startDate") val startDate: LocalDateTime?
) {
    constructor(
        endDate: LocalDateTime,
        noDevicesFound: Int,
        duration: Int,
        isManual: Boolean,
        scanMode: Int,
        startDate: LocalDateTime
    ): this(
        scanId = 0,
        endDate,
        noDevicesFound,
        duration,
        isManual,
        scanMode,
        startDate
    )

    constructor(
        startDate: LocalDateTime,
        isManual: Boolean,
        scanMode: Int,
    ): this (
        scanId = 0,
        endDate = null,
        noDevicesFound = null,
        duration = null,
        isManual = isManual,
        scanMode = scanMode,
        startDate = startDate,
            )

}
