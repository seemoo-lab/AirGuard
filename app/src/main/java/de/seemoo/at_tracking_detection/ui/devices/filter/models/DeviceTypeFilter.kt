package de.seemoo.at_tracking_detection.ui.devices.filter.models

import androidx.collection.ArraySet
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceType

class DeviceTypeFilter(initialDeviceTypes: Set<DeviceType>) : Filter() {

    val deviceTypes: ArraySet<DeviceType> = ArraySet()

    init {
        this.deviceTypes.addAll(initialDeviceTypes)
    }

    override fun apply(baseDevices: List<BaseDevice>): List<BaseDevice> {
        return baseDevices.filter {
            deviceTypes.contains(it.deviceType)
        }
    }

    fun contains(deviceType: DeviceType): Boolean = deviceTypes.contains(deviceType)

    fun add(deviceType: DeviceType) = deviceTypes.add(deviceType)

    fun remove(deviceType: DeviceType) = deviceTypes.remove(deviceType)
}