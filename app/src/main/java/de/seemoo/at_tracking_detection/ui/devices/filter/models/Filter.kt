package de.seemoo.at_tracking_detection.ui.devices.filter.models

import de.seemoo.at_tracking_detection.database.tables.device.Device

abstract class Filter {
    abstract fun apply(devices: List<Device>): List<Device>
}