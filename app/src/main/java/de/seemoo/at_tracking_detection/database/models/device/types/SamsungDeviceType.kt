package de.seemoo.at_tracking_detection.database.models.device.types

enum class SamsungDeviceType{
    SMART_TAG_1,
    SMART_TAG_1_PLUS,
    SMART_TAG_2,
    SOLUM,
    UNKNOWN;

    companion object {
        fun subTypeToString(subType: SamsungDeviceType): String {
            return when (subType) {
                SMART_TAG_1 -> "Samsung SmartTag"
                SMART_TAG_1_PLUS -> "Samsung SmartTag+"
                SMART_TAG_2 -> "Samsung SmartTag 2"
                SOLUM -> "Solum SmartTag"
                UNKNOWN -> "Unknown"
            }
        }
    }
}