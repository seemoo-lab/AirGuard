package de.seemoo.at_tracking_detection.database.models

import androidx.annotation.Keep
import androidx.room.*
import de.seemoo.at_tracking_detection.database.Converters
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.util.converter.DateTimeConverter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Entity(
    tableName = "beacon",
    indices = [
        Index(value = ["receivedAt"]),
        Index(value = ["deviceAddress"]),
        Index(value = ["connectionState"]),
        Index(value = ["deviceAddress", "receivedAt"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = BaseDevice::class,
            parentColumns = ["address"],
            childColumns = ["deviceAddress"],
            onDelete = ForeignKey.CASCADE  // Automatically deletes beacons when device is deleted
        )
    ]
)
@Keep
@TypeConverters(DateTimeConverter::class, Converters::class)
data class Beacon(
    @PrimaryKey(autoGenerate = true) val beaconId: Int,
    @ColumnInfo(name = "receivedAt") val receivedAt: LocalDateTime,
    @ColumnInfo(name = "rssi") val rssi: Int,
    @ColumnInfo(name = "deviceAddress") var deviceAddress: String,
    @ColumnInfo(name = "locationId") var locationId: Int?,
    @ColumnInfo(name = "mfg") var manufacturerData: ByteArray?,
    @ColumnInfo(name = "serviceUUIDs") var serviceUUIDs: List<String>?,
    @ColumnInfo(name = "connectionState", defaultValue = "UNKNOWN") var connectionState: String
) {
    constructor(
        receivedAt: LocalDateTime,
        rssi: Int,
        deviceAddress: String,
        locationId: Int?,
        mfg: ByteArray?,
        serviceUUIDs: List<String>?,
        connectionState: String
    ) : this(
        0,
        receivedAt,
        rssi,
        deviceAddress,
        locationId,
        mfg,
        serviceUUIDs,
        connectionState
    )

    fun getFormattedDate(): String =
        receivedAt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Beacon

        if (beaconId != other.beaconId) return false
        if (receivedAt != other.receivedAt) return false
        if (rssi != other.rssi) return false
        if (deviceAddress != other.deviceAddress) return false
        if (locationId != other.locationId) return false
        if (connectionState != other.connectionState) return false

        return true
    }

    override fun hashCode(): Int {
        var result = beaconId
            result = 31 * result + receivedAt.hashCode()
            result = 31 * result + rssi.hashCode()
            result = 31 * result + deviceAddress.hashCode()
            result = 31 * result + locationId.hashCode()
            return result
        }
    }