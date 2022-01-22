package de.seemoo.at_tracking_detection.ui.devices.filter.models

import de.seemoo.at_tracking_detection.database.models.device.BaseDevice

class IgnoredFilter : Filter() {
    override fun apply(baseDevices: List<BaseDevice>): List<BaseDevice> {
        return baseDevices.filter {
            it.ignore
        }
    }

    companion object {
        fun build(): Filter = IgnoredFilter()
    }
}