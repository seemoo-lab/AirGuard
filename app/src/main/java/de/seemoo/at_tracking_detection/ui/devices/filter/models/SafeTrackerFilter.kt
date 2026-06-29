package de.seemoo.at_tracking_detection.ui.devices.filter.models

import de.seemoo.at_tracking_detection.database.models.device.BaseDevice

class SafeTrackerFilter(private val filterFor: Boolean = false) : Filter() {
    override fun apply(baseDevices: List<BaseDevice>): List<BaseDevice> {
        return baseDevices.filter {
            if (filterFor) it.safeTracker else !it.safeTracker
        }
    }
}

