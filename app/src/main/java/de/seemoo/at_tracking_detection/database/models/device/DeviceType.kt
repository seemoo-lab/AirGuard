package de.seemoo.at_tracking_detection.database.models.device

import de.seemoo.at_tracking_detection.database.models.device.types.*
import de.seemoo.at_tracking_detection.util.SharedPrefs

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

        fun getAllowedDeviceTypesFromSettings(): List<DeviceType> {
            val validDeviceTypes = SharedPrefs.devicesFilter.toList()
            val allowedDeviceTypes = mutableListOf<DeviceType>()

            for (validDeviceType in validDeviceTypes) {
                when (validDeviceType) {
                    "airpods" -> allowedDeviceTypes.add(AIRPODS)
                    "airtags" -> allowedDeviceTypes.add(AIRTAG)
                    "apple_devices" -> allowedDeviceTypes.add(APPLE)
                    "chipolos" -> allowedDeviceTypes.add(CHIPOLO)
                    "find_my_devices" -> allowedDeviceTypes.add(FIND_MY)
                    "samsung_devices" -> allowedDeviceTypes.add(SAMSUNG)
                    "smart_tags" -> {
                        allowedDeviceTypes.add(GALAXY_SMART_TAG)
                        allowedDeviceTypes.add(GALAXY_SMART_TAG_PLUS)
                    }
                    "tiles" -> allowedDeviceTypes.add(TILE)
                }
            }

            return allowedDeviceTypes
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