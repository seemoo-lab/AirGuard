package de.seemoo.at_tracking_detection.database.models.device.types

import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R

enum class GoogleFindMyNetworkType {
    SMARTPHONE,
    TAG,
    UNKNOWN;

    companion object {
        fun visibleStringFromSubtype(subType: GoogleFindMyNetworkType): String {
            return when (subType) {
                SMARTPHONE -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.google_find_my_phone_name)
                TAG -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.google_find_my_tag_name)
                UNKNOWN -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.google_find_my_default_name)
            }
        }

        fun subTypeToString(subType: GoogleFindMyNetworkType): String {
            return when (subType) {
                SMARTPHONE -> "SMARTPHONE"
                TAG -> "TAG"
                UNKNOWN -> "UNKNOWN"
            }
        }

        fun stringToSubType(subType: String): GoogleFindMyNetworkType {
            return when (subType) {
                "SMARTPHONE" -> SMARTPHONE
                "TAG" -> TAG
                else -> UNKNOWN
            }
        }

        fun drawableForSubType(subType: GoogleFindMyNetworkType, deviceName: String? = null): Int {
            return when (subType) {
                SMARTPHONE -> R.drawable.ic_baseline_device_unknown_24
                TAG ->  if (deviceName != null) {
                            GoogleFindMyNetwork.getGoogleDrawableFromNameString(deviceName)
                        } else {
                            R.drawable.ic_baseline_device_unknown_24
                        }
                UNKNOWN -> R.drawable.ic_baseline_device_unknown_24
            }
        }
    }
}