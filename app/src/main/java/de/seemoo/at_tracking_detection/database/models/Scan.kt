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

    @ColumnInfo(name = "startDate") val startDate: LocalDateTime?,

    @ColumnInfo(name = "location_deg") var locationDeg: String?,

    @ColumnInfo(name = "location") var locationId: Int?,

    /**
     * Comma separated addresses of the devices found during the scan
     */
    @ColumnInfo(name = "device_addresses_found") var devicesAddressesFound: String?,
    /**
     *  Comma separated list of device types found during the scan
     */
    @ColumnInfo(name = "device_types_found") var devicesTypesFound: String?

) {
    constructor(
        endDate: LocalDateTime,
        noDevicesFound: Int,
        duration: Int,
        isManual: Boolean,
        scanMode: Int,
        startDate: LocalDateTime,
        locationDeg: String?,
        locationId: Int?,
        devicesAddressesFound: String?,
        devicesTypesFound: String?
    ): this(
        scanId = 0,
        endDate,
        noDevicesFound,
        duration,
        isManual,
        scanMode,
        startDate,
        locationDeg,
        locationId,
        devicesAddressesFound,
        devicesTypesFound
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
        locationDeg = null,
        locationId = null,
        devicesAddressesFound = null,
        devicesTypesFound = null
        )

}
