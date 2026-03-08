package de.seemoo.at_tracking_detection.ui.devices.filter.models

import de.seemoo.at_tracking_detection.database.models.device.BaseDevice

// filterFor:
// true: returns only devices where notificationSent is true
// false: returns only devices where notificationSent is false
class NotifiedFilter(private val filterFor: Boolean = true) : Filter() {
    override fun apply(baseDevices: List<BaseDevice>): List<BaseDevice> {
        return baseDevices.filter {
            if (filterFor) it.notificationSent else !it.notificationSent
        }
    }
}