package de.seemoo.at_tracking_detection.database.tables

import androidx.room.*
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.util.converter.DateTimeConverter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.experimental.and

@Entity(tableName = "device", indices = [Index(value = ["address"], unique = true)])
@TypeConverters(DateTimeConverter::class)
data class Device(
    @PrimaryKey(autoGenerate = true) var deviceId: Int,
    @ColumnInfo(name = "address") var address: String,
    @ColumnInfo(name = "ignore") val ignore: Boolean,
    @ColumnInfo(name = "connectable") val connectable: Boolean,
    @ColumnInfo(name = "payloadData") val payloadData: Byte?,
    @ColumnInfo(name = "firstDiscovery") val firstDiscovery: LocalDateTime,
    @ColumnInfo(name = "lastSeen") var lastSeen: LocalDateTime,
    @ColumnInfo(name = "notificationSent") var notificationSent: Boolean,
    @ColumnInfo(name = "lastNotificationSent") var lastNotificationSent: LocalDateTime?
) {

    constructor(
        address: String,
        ignore: Boolean,
        connectable: Boolean,
        payloadData: Byte?,
        firstDiscovery: LocalDateTime,
        lastSeen: LocalDateTime,
    ) : this(0, address, ignore, connectable, payloadData, firstDiscovery, lastSeen, false, null)

    private fun getDateTimeFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

    fun getFormattedDiscoveryDate(): String =
        firstDiscovery.format(getDateTimeFormatter())

    fun getFormattedLastSeenDate(): String =
        lastSeen.format(getDateTimeFormatter())

    fun isAirTag(): Boolean {
        return payloadData != null && payloadData.and(0x10).toInt() != 0
    }

    fun getDeviceName(): String {
        val resources = ATTrackingDetectionApplication.getAppContext().resources
        val airtag = de.seemoo.at_tracking_detection.R.string.device_name_airtag
        val findMyDevice = de.seemoo.at_tracking_detection.R.string.device_name_find_my_device
        return if (isAirTag()) {
            resources.getString(airtag).format(deviceId)
        } else {
            resources.getString(findMyDevice).format(deviceId)
        }
    }
}