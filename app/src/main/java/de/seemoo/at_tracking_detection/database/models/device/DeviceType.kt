package de.seemoo.at_tracking_detection.database.models.device

import de.seemoo.at_tracking_detection.R
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
    PEBBLEBEE,
    SAMSUNG,
    GALAXY_SMART_TAG,
    GALAXY_SMART_TAG_PLUS,
    GOOGLE_FIND_MY_NETWORK;

    companion object  {
        fun userReadableName(deviceType: DeviceType): String {
            return when (deviceType) {
                UNKNOWN -> Unknown.defaultDeviceName
                AIRPODS -> AirPods.defaultDeviceName
                AIRTAG -> AirTag.defaultDeviceName
                APPLE -> AppleDevice.defaultDeviceName
                FIND_MY -> AppleFindMy.defaultDeviceName
                TILE -> Tile.defaultDeviceName
                PEBBLEBEE -> PebbleBee.defaultDeviceName
                CHIPOLO -> Chipolo.defaultDeviceName
                SAMSUNG -> SamsungDevice.defaultDeviceName
                GALAXY_SMART_TAG -> SmartTag.defaultDeviceName
                GALAXY_SMART_TAG_PLUS -> SmartTagPlus.defaultDeviceName
                GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.defaultDeviceName
            }
        }

        fun getImageDrawable(deviceType: DeviceType): Int {
            return when (deviceType) {
                UNKNOWN -> R.drawable.ic_baseline_device_unknown_24
                AIRPODS -> R.drawable.ic_airpods
                AIRTAG -> R.drawable.ic_airtag
                APPLE -> R.drawable.ic_baseline_device_unknown_24
                FIND_MY -> R.drawable.ic_chipolo
                TILE -> R.drawable.ic_tile
                CHIPOLO -> R.drawable.ic_chipolo
                PEBBLEBEE -> R.drawable.ic_pebblebee_clip
                SAMSUNG -> R.drawable.ic_baseline_device_unknown_24
                GALAXY_SMART_TAG -> R.drawable.ic_smarttag_icon
                GALAXY_SMART_TAG_PLUS -> R.drawable.ic_smarttag_icon
                GOOGLE_FIND_MY_NETWORK -> R.drawable.ic_chipolo
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
                    "pebblebees" -> allowedDeviceTypes.add(PEBBLEBEE)
                    "google_find_my_network" -> allowedDeviceTypes.add(GOOGLE_FIND_MY_NETWORK)
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
            PEBBLEBEE -> true
            else -> false
        }
    }

    fun getNumberOfHoursToBeConsideredForTrackingDetection(): Long {
        return when (this) {
            TILE -> Tile.numberOfHoursToBeConsideredForTrackingDetection
            CHIPOLO -> Chipolo.numberOfHoursToBeConsideredForTrackingDetection
            PEBBLEBEE -> PebbleBee.numberOfHoursToBeConsideredForTrackingDetection
            UNKNOWN -> Unknown.numberOfHoursToBeConsideredForTrackingDetection
            AIRPODS -> AirPods.numberOfHoursToBeConsideredForTrackingDetection
            AIRTAG -> AirTag.numberOfHoursToBeConsideredForTrackingDetection
            APPLE -> AppleDevice.numberOfHoursToBeConsideredForTrackingDetection
            FIND_MY -> AppleFindMy.numberOfHoursToBeConsideredForTrackingDetection
            SAMSUNG -> SamsungDevice.numberOfHoursToBeConsideredForTrackingDetection
            GALAXY_SMART_TAG -> SmartTag.numberOfHoursToBeConsideredForTrackingDetection
            GALAXY_SMART_TAG_PLUS -> SmartTagPlus.numberOfHoursToBeConsideredForTrackingDetection
            GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.numberOfHoursToBeConsideredForTrackingDetection
        }
    }

    fun getNumberOfLocationsToBeConsideredForTrackingDetectionLow(): Int {
        return when (this) {
            TILE -> Tile.numberOfLocationsToBeConsideredForTrackingDetectionLow
            CHIPOLO -> Chipolo.numberOfLocationsToBeConsideredForTrackingDetectionLow
            PEBBLEBEE -> PebbleBee.numberOfLocationsToBeConsideredForTrackingDetectionLow
            UNKNOWN -> Unknown.numberOfLocationsToBeConsideredForTrackingDetectionLow
            AIRPODS -> AirPods.numberOfLocationsToBeConsideredForTrackingDetectionLow
            AIRTAG -> AirTag.numberOfLocationsToBeConsideredForTrackingDetectionLow
            APPLE -> AppleDevice.numberOfLocationsToBeConsideredForTrackingDetectionLow
            FIND_MY -> AppleFindMy.numberOfLocationsToBeConsideredForTrackingDetectionLow
            SAMSUNG -> SamsungDevice.numberOfLocationsToBeConsideredForTrackingDetectionLow
            GALAXY_SMART_TAG -> SmartTag.numberOfLocationsToBeConsideredForTrackingDetectionLow
            GALAXY_SMART_TAG_PLUS -> SmartTagPlus.numberOfLocationsToBeConsideredForTrackingDetectionLow
            GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.numberOfLocationsToBeConsideredForTrackingDetectionLow
        }
    }

    fun getNumberOfLocationsToBeConsideredForTrackingDetectionMedium(): Int {
        return when (this) {
            TILE -> Tile.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            CHIPOLO -> Chipolo.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            PEBBLEBEE -> PebbleBee.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            UNKNOWN -> Unknown.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            AIRPODS -> AirPods.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            AIRTAG -> AirTag.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            APPLE -> AppleDevice.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            FIND_MY -> AppleFindMy.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            SAMSUNG -> SamsungDevice.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            GALAXY_SMART_TAG -> SmartTag.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            GALAXY_SMART_TAG_PLUS -> SmartTagPlus.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.numberOfLocationsToBeConsideredForTrackingDetectionMedium
        }
    }

    fun getNumberOfLocationsToBeConsideredForTrackingDetectionHigh(): Int {
        return when (this) {
            TILE -> Tile.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            CHIPOLO -> Chipolo.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            PEBBLEBEE -> PebbleBee.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            UNKNOWN -> Unknown.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            AIRPODS -> AirPods.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            AIRTAG -> AirTag.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            APPLE -> AppleDevice.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            FIND_MY -> AppleFindMy.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            SAMSUNG -> SamsungDevice.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            GALAXY_SMART_TAG -> SmartTag.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            GALAXY_SMART_TAG_PLUS -> SmartTagPlus.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.numberOfLocationsToBeConsideredForTrackingDetectionHigh
        }
    }
}