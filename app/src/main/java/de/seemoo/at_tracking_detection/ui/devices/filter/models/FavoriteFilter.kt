package de.seemoo.at_tracking_detection.ui.devices.filter.models

import de.seemoo.at_tracking_detection.database.models.device.BaseDevice

// filterFor: true -> only hearted / favourite, false -> only no heart / not favourite
class FavoriteFilter(private val filterFor: Boolean = true) : Filter() {
    override fun apply(baseDevices: List<BaseDevice>): List<BaseDevice> {
        return baseDevices.filter { if (filterFor) it.hearted else !it.hearted }
    }
}

