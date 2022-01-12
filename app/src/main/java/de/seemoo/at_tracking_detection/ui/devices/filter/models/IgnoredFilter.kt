package de.seemoo.at_tracking_detection.ui.devices.filter.models

import de.seemoo.at_tracking_detection.database.tables.device.Device

class IgnoredFilter : Filter() {
    override fun apply(devices: List<Device>): List<Device> {
        return devices.filter {
            it.ignore
        }
    }

    companion object {
        fun build(): Filter = IgnoredFilter()
    }
}