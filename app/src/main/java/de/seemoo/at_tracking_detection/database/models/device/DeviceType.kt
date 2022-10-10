package de.seemoo.at_tracking_detection.database.models.device

import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.types.*

enum class DeviceType {
    UNKNOWN,
    AIRTAG,
    APPLE,
    AIRPODS,
    TILE,
    FIND_MY,
    CHIPOLO_ONE,
    CHIPOLO_ONE_SPOT,
    CHIPOLO_CARD,
    CHIPOLO_CARD_SPOT,
    GALAXY_SMART_TAG;

    companion object  {
        fun userReadableName(deviceType: DeviceType): String {
            return when (deviceType) {
                UNKNOWN -> Unknown.defaultDeviceName
                AIRPODS -> AirPods.defaultDeviceName
                AIRTAG -> AirTag.defaultDeviceName
                APPLE -> AppleDevice.defaultDeviceName
                FIND_MY -> FindMy.defaultDeviceName
                TILE -> Tile.defaultDeviceName
                GALAXY_SMART_TAG -> SmartTag.defaultDeviceName
                else -> Unknown.defaultDeviceName
            }
        }
    }

    fun canBeIgnored(): Boolean {
        return when (this) {
            TILE -> true
            GALAXY_SMART_TAG -> true
            APPLE -> true
            else -> false
        }
    }
}