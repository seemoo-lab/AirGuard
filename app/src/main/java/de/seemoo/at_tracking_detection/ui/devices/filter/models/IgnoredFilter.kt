package de.seemoo.at_tracking_detection.ui.devices.filter.models

import de.seemoo.at_tracking_detection.database.models.device.BaseDevice

// filterFor:
// true: returns only devices that are ignored
// false: returns only devices that are not ignored
class IgnoredFilter(private val filterFor: Boolean = true) : Filter() {
    override fun apply(baseDevices: List<BaseDevice>): List<BaseDevice> {
        return baseDevices.filter {
            if (filterFor) it.ignore else !it.ignore
        }
    }
}