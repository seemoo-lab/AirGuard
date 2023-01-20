package de.seemoo.at_tracking_detection.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import de.seemoo.at_tracking_detection.database.Converters
import de.seemoo.at_tracking_detection.util.converter.DateTimeConverter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Entity(tableName = "beacon")
@TypeConverters(DateTimeConverter::class, Converters::class)
data class Beacon(
    @PrimaryKey(autoGenerate = true) val beaconId: Int,
    @ColumnInfo(name = "receivedAt") val receivedAt: LocalDateTime,
    @ColumnInfo(name = "rssi") val rssi: Int,
    @ColumnInfo(name = "deviceAddress") var deviceAddress: String,
    @ColumnInfo(name = "locationId") var locationId: Int?,
    @ColumnInfo(name = "mfg") var manufacturerData: ByteArray?,
    @ColumnInfo(name = "serviceUUIDs") var serviceUUIDs: List<String>?
) {
    constructor(
        receivedAt: LocalDateTime,
        rssi: Int,
        deviceAddress: String,
        /* latitude: Double?,
        latitude: Double?,*/
        locationId: Int?,
        mfg: ByteArray?,
        serviceUUIDs: List<String>?
    ) : this(
        0,
        receivedAt,
        rssi,
        deviceAddress,
        /* longitude,
        latitude,*/
        locationId,
        mfg,
        serviceUUIDs
    )

    fun getFormattedDate(): String =
        receivedAt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))

    fun getKey(): ByteArray? {
        return manufacturerData?.get(22)?.let { // TODO: test, maybe also use in ScanBluetoothWorker with ServiceData instead of manufacturerData???
            byteArrayOf(
                manufacturerData?.get(15)!!,
                manufacturerData?.get(16)!!,
                manufacturerData?.get(17)!!,
                manufacturerData?.get(18)!!,
                manufacturerData?.get(19)!!,
                manufacturerData?.get(20)!!,
                manufacturerData?.get(21)!!,
                it,)
        }
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Beacon

        if (beaconId != other.beaconId) return false
        if (receivedAt != other.receivedAt) return false
        if (rssi != other.rssi) return false
        if (deviceAddress != other.deviceAddress) return false
        if (locationId != other.locationId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = beaconId
            result = 31 * result + receivedAt.hashCode()
            result = 31 * result + rssi
            result = 31 * result + deviceAddress.hashCode()
            result = 31 * result + locationId.hashCode()
            return result
        }
    }