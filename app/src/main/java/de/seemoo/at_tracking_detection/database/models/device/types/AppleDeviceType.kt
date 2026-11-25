package de.seemoo.at_tracking_detection.database.models.device.types

import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R

enum class AppleDeviceType(
    val placeholderName: String,
    @DrawableRes val drawableRes: Int
) {
    IPHONE("iPhone", R.drawable.ic_baseline_device_unknown_24),
    IPAD("iPad", R.drawable.ic_baseline_device_unknown_24),
    IPOD("iPod", R.drawable.ic_baseline_device_unknown_24),
    MAC("Mac", R.drawable.ic_baseline_device_unknown_24),
    APPLE_WATCH("Apple Watch", R.drawable.ic_apple_watch),
    APPLE_TV("Apple TV", R.drawable.ic_baseline_device_unknown_24),
    HOMEPOD("Apple HomePod", R.drawable.ic_baseline_device_unknown_24),
    APPLE_HOMEVIEW("Apple HomeView", R.drawable.ic_baseline_device_unknown_24),
    APPLE_VISION_PRO("Apple Vision Pro", R.drawable.ic_baseline_device_unknown_24),
    AIRPODS("AirPods", R.drawable.ic_airpods),
    AIRTAG("AirTag", R.drawable.ic_airtag),
    UNKNOWN("Apple Device", R.drawable.ic_baseline_device_unknown_24);

    companion object {
        fun visibleStringFromSubtype(subType: AppleDeviceType): String =
            if (subType == UNKNOWN) {
                ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.apple_device_default_name)
            } else {
                subType.placeholderName
            }

        fun subTypeToString(subType: AppleDeviceType): String = subType.name

        fun stringToSubType(subType: String?): AppleDeviceType {
            if (subType.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { it.name.equals(subType, ignoreCase = true) }
                ?: fromModelIdentifier(subType)
        }

        fun drawableForSubType(subType: AppleDeviceType): Int = subType.drawableRes

        fun fromModelIdentifier(modelIdentifier: String?): AppleDeviceType {
            val trimmed = modelIdentifier?.trim()?.takeIf { it.isNotEmpty() } ?: return UNKNOWN
            return when {
                trimmed.startsWith("iPhone", ignoreCase = true) -> IPHONE
                trimmed.startsWith("iPad", ignoreCase = true) -> IPAD
                trimmed.startsWith("iPod", ignoreCase = true) -> IPOD
                trimmed.startsWith("Mac", ignoreCase = true) ||
                        trimmed.startsWith("iMac", ignoreCase = true) -> MAC
                trimmed.startsWith("Watch", ignoreCase = true) -> APPLE_WATCH
                trimmed.startsWith("AppleTV", ignoreCase = true) -> APPLE_TV
                trimmed.startsWith("AudioAccessory", ignoreCase = true) -> HOMEPOD
                trimmed.startsWith("HomeAccessory", ignoreCase = true) -> APPLE_HOMEVIEW
                trimmed.startsWith("RealityDevice", ignoreCase = true) -> APPLE_VISION_PRO
                trimmed.startsWith("AirPods", ignoreCase = true) -> AIRPODS
                trimmed.startsWith("AirTag", ignoreCase = true) -> AIRTAG
                else -> UNKNOWN
            }
        }

        fun parseMajorVersion(modelIdentifier: String?): Int? =
            modelIdentifier?.trim()
                ?.substringBefore(",")
                ?.takeLastWhile(Char::isDigit)
                ?.toIntOrNull()
    }
}
