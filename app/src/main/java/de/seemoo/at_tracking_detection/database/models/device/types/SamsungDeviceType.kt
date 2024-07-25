package de.seemoo.at_tracking_detection.database.models.device.types

import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R

enum class SamsungDeviceType{
    SMART_TAG_1,
    SMART_TAG_1_PLUS,
    SMART_TAG_2,
    SOLUM,
    UNKNOWN;

    companion object {
        fun visibleStringFromSubtype(subType: SamsungDeviceType): String {
            return when (subType) {
                SMART_TAG_1 -> "SmartTag"
                SMART_TAG_1_PLUS -> "SmartTag+"
                SMART_TAG_2 -> "SmartTag 2"
                SOLUM -> "Solum SmartTag"
                UNKNOWN -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.samsung_device_name)
            }
        }

        fun subTypeToString(subType: SamsungDeviceType): String {
            return when (subType) {
                SMART_TAG_1 -> "SMART_TAG_1"
                SMART_TAG_1_PLUS -> "SMART_TAG_1_PLUS"
                SMART_TAG_2 -> "SMART_TAG_2"
                SOLUM -> "SOLUM"
                UNKNOWN -> "UNKNOWN"
            }
        }

        fun stringToSubType(subType: String): SamsungDeviceType {
            return when (subType) {
                "SMART_TAG_1" -> SMART_TAG_1
                "SMART_TAG_1_PLUS" -> SMART_TAG_1_PLUS
                "SMART_TAG_2" -> SMART_TAG_2
                "SOLUM" -> SOLUM
                else -> UNKNOWN
            }
        }

        fun drawableForSubType(subType: SamsungDeviceType): Int {
            return when (subType) {
                SMART_TAG_1 -> R.drawable.ic_smarttag_icon
                SMART_TAG_1_PLUS -> R.drawable.ic_smarttag_icon
                SMART_TAG_2 -> R.drawable.ic_baseline_device_unknown_24 // TODO
                SOLUM -> R.drawable.ic_baseline_device_unknown_24 // TODO
                UNKNOWN -> R.drawable.ic_baseline_device_unknown_24
            }
        }
    }
}