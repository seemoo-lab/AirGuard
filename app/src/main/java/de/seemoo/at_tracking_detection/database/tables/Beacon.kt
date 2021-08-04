package de.seemoo.at_tracking_detection.database.tables

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import de.seemoo.at_tracking_detection.util.converter.DateTimeConverter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Entity(tableName = "beacon")
@TypeConverters(DateTimeConverter::class)
data class Beacon(
    @PrimaryKey(autoGenerate = true) val beaconId: Int,
    @ColumnInfo(name = "receivedAt") val receivedAt: LocalDateTime,
    @ColumnInfo(name = "rssi") val rssi: Int,
    @ColumnInfo(name = "deviceAddress") var deviceAddress: String,
    @ColumnInfo(name = "longitude") var longitude: Double?,
    @ColumnInfo(name = "latitude") var latitude: Double?,
    @ColumnInfo(name = "mfg") var manufacturerData: ByteArray?
) {
    constructor(
        receivedAt: LocalDateTime,
        rssi: Int,
        deviceAddress: String,
        longitude: Double?,
        latitude: Double?,
        mfg: ByteArray?
    ) : this(
        0,
        receivedAt,
        rssi,
        deviceAddress,
        longitude,
        latitude,
        mfg
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
        if (longitude != other.longitude) return false
        if (latitude != other.latitude) return false

        return true
    }

    override fun hashCode(): Int {
        var result = beaconId
        result = 31 * result + receivedAt.hashCode()
        result = 31 * result + rssi
        result = 31 * result + deviceAddress.hashCode()
        result = 31 * result + (longitude?.hashCode() ?: 0)
        result = 31 * result + (latitude?.hashCode() ?: 0)
        return result
    }
}