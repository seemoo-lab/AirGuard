package de.seemoo.at_tracking_detection.ui.devices.filter.models

import de.seemoo.at_tracking_detection.database.models.device.BaseDevice

abstract class Filter {
    abstract fun apply(baseDevices: List<BaseDevice>): List<BaseDevice>
}