package de.seemoo.at_tracking_detection.ui.devices.filter.models

import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator

class LocationFilter(private val locationId: Int) : Filter() {
    override fun apply(baseDevices: List<BaseDevice>): List<BaseDevice> {
        val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
        val relevantTrackingDate = RiskLevelEvaluator.relevantTrackingDateForRiskCalculation
        val devicesAtLocation = deviceRepository.getDevicesAtLocation(locationId, relevantTrackingDate)
        val deviceAddresses = devicesAtLocation.map { it.address }.toSet()

        return baseDevices.filter { deviceAddresses.contains(it.address) }
    }

    companion object {
        fun build(locationId: Int): Filter {
            return LocationFilter(locationId)
        }
    }
}

