package de.seemoo.at_tracking_detection.ui.devices.filter.models

import de.seemoo.at_tracking_detection.database.tables.Device

class NotifiedFilter : Filter() {
    override fun apply(devices: List<Device>): List<Device> {
        return devices.filter {
            it.notificationSent
        }
    }

    companion object {
        fun build(): Filter = NotifiedFilter()
    }
}
