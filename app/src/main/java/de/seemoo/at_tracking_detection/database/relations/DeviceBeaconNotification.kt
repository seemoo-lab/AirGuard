package de.seemoo.at_tracking_detection.database.relations

import androidx.room.Relation
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Notification
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import java.time.LocalDateTime


data class DeviceBeaconNotification(
    var deviceId: Int,
    var address: String,
    val ignore: Boolean,
    val connectable: Boolean,
    val payloadData: Byte?,
    val firstDiscovery: LocalDateTime,
    val lastSeen: LocalDateTime,
    val deviceType: DeviceType,
    @Relation(parentColumn = "address", entityColumn = "deviceAddress")
    val beacons: List<Beacon>,
    @Relation(
        parentColumn = "address",
        entityColumn = "deviceAddress",
        entity = Notification::class
    )
    val notifications: List<NotificationFeedback>
)
