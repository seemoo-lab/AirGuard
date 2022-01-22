package de.seemoo.at_tracking_detection.ui.devices.filter.models

import de.seemoo.at_tracking_detection.database.models.device.BaseDevice

class NotifiedFilter : Filter() {
    override fun apply(baseDevices: List<BaseDevice>): List<BaseDevice> {
        return baseDevices.filter {
            it.notificationSent
        }
    }

    companion object {
        fun build(): Filter = NotifiedFilter()
    }
}
