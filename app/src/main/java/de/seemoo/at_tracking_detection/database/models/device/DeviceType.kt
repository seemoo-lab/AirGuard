package de.seemoo.at_tracking_detection.database.models.device

import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.types.*
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.SharedPrefs

enum class DeviceType {
    UNKNOWN,
    AIRTAG,
    APPLE,
    AIRPODS,
    TILE,
    FIND_MY,
    CHIPOLO,
    SAMSUNG_DEVICE,
    GOOGLE_FIND_MY_NETWORK;

    companion object  {
        fun userReadableNameDefault(deviceType: DeviceType): String {
            return when (deviceType) {
                UNKNOWN -> Unknown.defaultDeviceName
                AIRPODS -> AirPods.defaultDeviceName
                AIRTAG -> AirTag.defaultDeviceName
                APPLE -> AppleDevice.defaultDeviceName
                FIND_MY -> FindMy.defaultDeviceName
                TILE -> Tile.defaultDeviceName
                CHIPOLO -> Chipolo.defaultDeviceName
                SAMSUNG_DEVICE -> SamsungDevice.defaultDeviceName
                GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.defaultDeviceName
            }
        }

        fun userReadableName(wrappedScanResult: ScanResultWrapper): String {
            val deviceType: DeviceType = wrappedScanResult.deviceType
            return when (deviceType) {
                UNKNOWN -> Unknown.defaultDeviceName
                AIRPODS -> AirPods.defaultDeviceName
                AIRTAG -> AirTag.defaultDeviceName
                APPLE -> AppleDevice.defaultDeviceName
                FIND_MY -> FindMy.defaultDeviceName
                TILE -> Tile.defaultDeviceName
                CHIPOLO -> Chipolo.defaultDeviceName
                SAMSUNG_DEVICE -> {
                    val subType: SamsungDeviceType = SamsungDevice.getSubType(wrappedScanResult)
                    when (subType) {
                        SamsungDeviceType.SMART_TAG_1 -> "SmartTag"
                        SamsungDeviceType.SMART_TAG_1_PLUS -> "SmartTag Plus"
                        SamsungDeviceType.SMART_TAG_2 -> "SmartTag 2"
                        SamsungDeviceType.SOLUM -> "SOLUM SmartTag"
                        SamsungDeviceType.UNKNOWN -> "Samsung Device"
                    }
                }
                GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.defaultDeviceName
            }
        }

        fun getImageDrawable(wrappedScanResult: ScanResultWrapper): Int {
            val deviceType: DeviceType = wrappedScanResult.deviceType
            return when (deviceType) {
                UNKNOWN -> R.drawable.ic_baseline_device_unknown_24
                AIRPODS -> R.drawable.ic_airpods
                AIRTAG -> R.drawable.ic_airtag
                APPLE -> R.drawable.ic_baseline_device_unknown_24
                FIND_MY -> R.drawable.ic_chipolo
                TILE -> R.drawable.ic_tile
                CHIPOLO -> R.drawable.ic_chipolo
                SAMSUNG_DEVICE -> {
                    val subType: SamsungDeviceType = SamsungDevice.getSubType(wrappedScanResult)
                    when (subType) {
                        SamsungDeviceType.SMART_TAG_1 -> R.drawable.ic_smarttag_icon
                        SamsungDeviceType.SMART_TAG_1_PLUS -> R.drawable.ic_smarttag_icon
                        SamsungDeviceType.SMART_TAG_2 -> R.drawable.ic_baseline_device_unknown_24
                        SamsungDeviceType.SOLUM -> R.drawable.ic_baseline_device_unknown_24
                        SamsungDeviceType.UNKNOWN -> R.drawable.ic_baseline_device_unknown_24
                    }
                }
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
                    "smart_tags" -> allowedDeviceTypes.add(SAMSUNG_DEVICE)
                    "tiles" -> allowedDeviceTypes.add(TILE)
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
            else -> false
        }
    }

    fun getNumberOfHoursToBeConsideredForTrackingDetection(): Long {
        return when (this) {
            TILE -> Tile.numberOfHoursToBeConsideredForTrackingDetection
            CHIPOLO -> Chipolo.numberOfHoursToBeConsideredForTrackingDetection
            UNKNOWN -> Unknown.numberOfHoursToBeConsideredForTrackingDetection
            AIRPODS -> AirPods.numberOfHoursToBeConsideredForTrackingDetection
            AIRTAG -> AirTag.numberOfHoursToBeConsideredForTrackingDetection
            APPLE -> AppleDevice.numberOfHoursToBeConsideredForTrackingDetection
            FIND_MY -> FindMy.numberOfHoursToBeConsideredForTrackingDetection
            SAMSUNG_DEVICE -> SamsungDevice.numberOfHoursToBeConsideredForTrackingDetection
            GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.numberOfHoursToBeConsideredForTrackingDetection
        }
    }

    fun getNumberOfLocationsToBeConsideredForTrackingDetectionLow(): Int {
        return when (this) {
            TILE -> Tile.numberOfLocationsToBeConsideredForTrackingDetectionLow
            CHIPOLO -> Chipolo.numberOfLocationsToBeConsideredForTrackingDetectionLow
            UNKNOWN -> Unknown.numberOfLocationsToBeConsideredForTrackingDetectionLow
            AIRPODS -> AirPods.numberOfLocationsToBeConsideredForTrackingDetectionLow
            AIRTAG -> AirTag.numberOfLocationsToBeConsideredForTrackingDetectionLow
            APPLE -> AppleDevice.numberOfLocationsToBeConsideredForTrackingDetectionLow
            FIND_MY -> FindMy.numberOfLocationsToBeConsideredForTrackingDetectionLow
            SAMSUNG_DEVICE -> SamsungDevice.numberOfLocationsToBeConsideredForTrackingDetectionLow
            GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.numberOfLocationsToBeConsideredForTrackingDetectionLow
        }
    }

    fun getNumberOfLocationsToBeConsideredForTrackingDetectionMedium(): Int {
        return when (this) {
            TILE -> Tile.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            CHIPOLO -> Chipolo.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            UNKNOWN -> Unknown.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            AIRPODS -> AirPods.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            AIRTAG -> AirTag.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            APPLE -> AppleDevice.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            FIND_MY -> FindMy.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            SAMSUNG_DEVICE -> SamsungDevice.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.numberOfLocationsToBeConsideredForTrackingDetectionMedium
        }
    }

    fun getNumberOfLocationsToBeConsideredForTrackingDetectionHigh(): Int {
        return when (this) {
            TILE -> Tile.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            CHIPOLO -> Chipolo.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            UNKNOWN -> Unknown.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            AIRPODS -> AirPods.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            AIRTAG -> AirTag.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            APPLE -> AppleDevice.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            FIND_MY -> FindMy.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            SAMSUNG_DEVICE -> SamsungDevice.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.numberOfLocationsToBeConsideredForTrackingDetectionHigh
        }
    }
}