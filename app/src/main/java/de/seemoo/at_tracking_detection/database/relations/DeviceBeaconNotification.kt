package de.seemoo.at_tracking_detection.database.relations

import androidx.annotation.Keep
import androidx.room.Relation
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Notification
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import java.time.LocalDateTime

@Keep
data class DeviceBeaconNotification(
    var deviceId: Int,
    val uniqueId: String,
    var address: String,
    val ignore: Boolean,
    val connectable: Boolean,
    val payloadData: Byte?,
    val firstDiscovery: LocalDateTime,
    val lastSeen: LocalDateTime,
    val deviceType: DeviceType,
    @Relation(parentColumn = "address", entityColumn = "deviceAddress")
    var beacons: List<Beacon>,
    @Relation(
        parentColumn = "address",
        entityColumn = "deviceAddress",
        entity = Notification::class
    )
    var notifications: List<NotificationFeedback>
)
