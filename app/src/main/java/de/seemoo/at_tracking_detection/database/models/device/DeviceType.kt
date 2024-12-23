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
    PEBBLEBEE,
    SAMSUNG_TRACKER,
    SAMSUNG_FIND_MY_MOBILE,
    GOOGLE_FIND_MY_NETWORK;

    companion object  {
        fun userReadableNameDefault(deviceType: DeviceType): String {
            return when (deviceType) {
                UNKNOWN -> Unknown.defaultDeviceName
                AIRPODS -> AirPods.defaultDeviceName
                AIRTAG -> AirTag.defaultDeviceName
                APPLE -> AppleDevice.defaultDeviceName
                FIND_MY -> AppleFindMy.defaultDeviceName
                TILE -> Tile.defaultDeviceName
                PEBBLEBEE -> PebbleBee.defaultDeviceName
                CHIPOLO -> Chipolo.defaultDeviceName
                SAMSUNG_TRACKER -> SamsungTracker.defaultDeviceName
                SAMSUNG_FIND_MY_MOBILE -> SamsungFindMyMobile.defaultDeviceName
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
                FIND_MY -> AppleFindMy.defaultDeviceName
                TILE -> Tile.defaultDeviceName
                CHIPOLO -> Chipolo.defaultDeviceName
                PEBBLEBEE -> PebbleBee.defaultDeviceName
                SAMSUNG_TRACKER -> SamsungTracker.defaultDeviceName
                SAMSUNG_FIND_MY_MOBILE -> SamsungFindMyMobile.defaultDeviceName
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
                PEBBLEBEE -> R.drawable.ic_pebblebee_clip
                SAMSUNG_TRACKER -> R.drawable.ic_smarttag_icon
                SAMSUNG_FIND_MY_MOBILE -> R.drawable.ic_baseline_device_unknown_24
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
                    "smart_tags" -> allowedDeviceTypes.add(SAMSUNG_TRACKER)
                    "samsung_find_my_mobile" -> allowedDeviceTypes.add(SAMSUNG_FIND_MY_MOBILE)
                    "tiles" -> allowedDeviceTypes.add(TILE)
                    "pebblebees" -> allowedDeviceTypes.add(PEBBLEBEE)
                    "google_find_my_network" -> allowedDeviceTypes.add(GOOGLE_FIND_MY_NETWORK)
                }
            }

            return allowedDeviceTypes
        }

    }

    fun canBeIgnored(cs: ConnectionState? = null): Boolean {
        // Only Devices with a constant identifier can be ignored
        return when (this) {
            TILE -> true
            CHIPOLO -> true
            PEBBLEBEE -> true
            GOOGLE_FIND_MY_NETWORK -> cs == ConnectionState.OVERMATURE_OFFLINE
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
            SAMSUNG_TRACKER -> SamsungTracker.numberOfHoursToBeConsideredForTrackingDetection
            SAMSUNG_FIND_MY_MOBILE -> SamsungFindMyMobile.numberOfHoursToBeConsideredForTrackingDetection
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
            SAMSUNG_TRACKER -> SamsungTracker.numberOfLocationsToBeConsideredForTrackingDetectionLow
            SAMSUNG_FIND_MY_MOBILE -> SamsungFindMyMobile.numberOfLocationsToBeConsideredForTrackingDetectionLow
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
            SAMSUNG_TRACKER -> SamsungTracker.numberOfLocationsToBeConsideredForTrackingDetectionMedium
            SAMSUNG_FIND_MY_MOBILE -> SamsungFindMyMobile.numberOfLocationsToBeConsideredForTrackingDetectionMedium
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
            SAMSUNG_TRACKER -> SamsungTracker.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            SAMSUNG_FIND_MY_MOBILE -> SamsungFindMyMobile.numberOfLocationsToBeConsideredForTrackingDetectionHigh
            GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.numberOfLocationsToBeConsideredForTrackingDetectionHigh
        }
    }
}