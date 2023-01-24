package de.seemoo.at_tracking_detection.database.models.device

import android.bluetooth.le.ScanResult
import android.os.Build
import androidx.room.*
import de.seemoo.at_tracking_detection.database.models.device.types.*
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungDevice.Companion.getPublicKey
import de.seemoo.at_tracking_detection.util.converter.DateTimeConverter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import kotlin.experimental.and

@Entity(tableName = "device", indices = [Index(value = ["address"], unique = true)])
@TypeConverters(DateTimeConverter::class)
data class BaseDevice(
    @PrimaryKey(autoGenerate = true) var deviceId: Int,
    @ColumnInfo(name = "uniqueId") val uniqueId: String?,
    @ColumnInfo(name = "address") var address: String,
    @ColumnInfo(name = "name") var name: String?,
    @ColumnInfo(name = "ignore") val ignore: Boolean,
    @ColumnInfo(name = "connectable", defaultValue = "0") val connectable: Boolean?,
    @ColumnInfo(name = "payloadData") val payloadData: Byte?,
    @ColumnInfo(name = "firstDiscovery") val firstDiscovery: LocalDateTime,
    @ColumnInfo(name = "lastSeen") var lastSeen: LocalDateTime,
    @ColumnInfo(name = "notificationSent") var notificationSent: Boolean,
    @ColumnInfo(name = "lastNotificationSent") var lastNotificationSent: LocalDateTime?,
    @ColumnInfo(name = "deviceType") val deviceType: DeviceType?
) {

    constructor(
        address: String,
        ignore: Boolean,
        connectable: Boolean,
        payloadData: Byte?,
        firstDiscovery: LocalDateTime,
        lastSeen: LocalDateTime,
        deviceType: DeviceType
    ) : this(
        0,
        UUID.randomUUID().toString(),
        address,
        null,
        ignore,
        connectable,
        payloadData,
        firstDiscovery,
        lastSeen,
        false,
        null,
        deviceType
    )

    constructor(scanResult: ScanResult) : this(
        0,
        UUID.randomUUID().toString(),
        getPublicKey(scanResult),
        getDeviceName(scanResult),
        false,
        scanResult.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                scanResult.isConnectable
            } else {
                null
            }
        },
        scanResult.scanRecord?.getManufacturerSpecificData(76)?.get(2),
        LocalDateTime.now(), LocalDateTime.now(), false, null,
        DeviceManager.getDeviceType(scanResult)
    )

    fun getDeviceNameWithID(): String = name ?: device.defaultDeviceNameWithId

    @Ignore
    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

    @Ignore
    val device: Device = when (deviceType) {
        DeviceType.AIRTAG -> AirTag(deviceId)
        DeviceType.UNKNOWN -> Unknown(deviceId)
        DeviceType.APPLE -> AppleDevice(deviceId)
        DeviceType.AIRPODS -> AirPods(deviceId)
        DeviceType.FIND_MY -> FindMy(deviceId)
        DeviceType.TILE -> Tile(deviceId)
        DeviceType.SAMSUNG -> SamsungDevice(deviceId)
        DeviceType.GALAXY_SMART_TAG -> SmartTag(deviceId)
        DeviceType.GALAXY_SMART_TAG_PLUS -> SmartTagPlus(deviceId)
        else -> {
            // For backwards compatibility
            if (payloadData?.and(0x10)?.toInt() != 0 && connectable == true) {
                AirTag(deviceId)
            } else {
                Unknown(deviceId)
            }
        }
    }

    fun getFormattedDiscoveryDate(): String = firstDiscovery.format(dateTimeFormatter)

    fun getFormattedLastSeenDate(): String = lastSeen.format(dateTimeFormatter)

    companion object {
        fun getDeviceName(scanResult: ScanResult): String? {
            return when (DeviceManager.getDeviceType(scanResult)) {
                DeviceType.GALAXY_SMART_TAG_PLUS -> null
                DeviceType.GALAXY_SMART_TAG -> null
                else -> scanResult.scanRecord?.deviceName
            }
        }

        fun getConnectionState(scanResult: ScanResult): ConnectionState {
            return when (DeviceManager.getDeviceType(scanResult)) {
                DeviceType.TILE -> Tile.getConnectionState(scanResult)
                DeviceType.SAMSUNG -> SamsungDevice.getConnectionState(scanResult)
                DeviceType.GALAXY_SMART_TAG -> SamsungDevice.getConnectionState(scanResult)
                DeviceType.GALAXY_SMART_TAG_PLUS -> SamsungDevice.getConnectionState(scanResult)
                DeviceType.AIRPODS -> AppleDevice.getConnectionState(scanResult)
                DeviceType.FIND_MY -> AppleDevice.getConnectionState(scanResult)
                DeviceType.AIRTAG -> AppleDevice.getConnectionState(scanResult)
                DeviceType.APPLE -> AppleDevice.getConnectionState(scanResult)
                else -> ConnectionState.UNKNOWN
            }
        }
    }
}