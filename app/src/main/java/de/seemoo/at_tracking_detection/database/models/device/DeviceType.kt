package de.seemoo.at_tracking_detection.database.models.device

import de.seemoo.at_tracking_detection.database.models.device.types.*

enum class DeviceType {
    UNKNOWN,
    AIRTAG,
    APPLE,
    AIRPODS,
    TILE,
    FIND_MY,
    CHIPOLO,
    SAMSUNG,
    GALAXY_SMART_TAG,
    GALAXY_SMART_TAG_PLUS;

    companion object  {
        fun userReadableName(deviceType: DeviceType): String {
            return when (deviceType) {
                UNKNOWN -> Unknown.defaultDeviceName
                AIRPODS -> AirPods.defaultDeviceName
                AIRTAG -> AirTag.defaultDeviceName
                APPLE -> AppleDevice.defaultDeviceName
                FIND_MY -> FindMy.defaultDeviceName
                TILE -> Tile.defaultDeviceName
                CHIPOLO -> Chipolo.defaultDeviceName
                SAMSUNG -> SamsungDevice.defaultDeviceName
                GALAXY_SMART_TAG -> SmartTag.defaultDeviceName
                GALAXY_SMART_TAG_PLUS -> SmartTagPlus.defaultDeviceName
            }
        }
    }

    fun canBeIgnored(): Boolean {
        // Only Devices with a constant identifier can be ignored
        return when (this) {
            TILE -> true
            CHIPOLO -> true
            else -> false
        }
    }
}