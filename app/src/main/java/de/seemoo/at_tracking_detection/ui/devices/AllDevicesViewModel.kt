package de.seemoo.at_tracking_detection.ui.devices

import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.util.risk.RiskLevel
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import javax.inject.Inject

@HiltViewModel
class AllDevicesViewModel @Inject constructor(
    riskLevelEvaluator: RiskLevelEvaluator,
    deviceRepository: DeviceRepository
): ViewModel() {

    val countNotTracking = deviceRepository.countNotTracking.asLiveData()
    val countIgnored = deviceRepository.countIgnored.asLiveData()
    val countTracking = deviceRepository.trackingDevicesNotIgnoredSinceCount(RiskLevelEvaluator.relevantTrackingDateForRiskCalculation).asLiveData()

    val countAirTags = deviceRepository.countForDeviceType(DeviceType.AIRTAG).asLiveData()
    val countFindMy = deviceRepository.countForDeviceType(DeviceType.FIND_MY).asLiveData()
    val countTile = deviceRepository.countForDeviceType(DeviceType.TILE).asLiveData()
    val countChipolo = deviceRepository.countForDeviceType(DeviceType.CHIPOLO).asLiveData()
    val countGoogle = deviceRepository.countForDeviceType(DeviceType.GOOGLE_FIND_MY_NETWORK).asLiveData()
    val countPebblebee = deviceRepository.countForDeviceType(DeviceType.PEBBLEBEE).asLiveData()
    val countSmartTag = deviceRepository.countForDeviceType(DeviceType.SAMSUNG_TRACKER).asLiveData()

    var riskColor: Int

    init {
        val context = ATTrackingDetectionApplication.getAppContext()
        riskColor = when (riskLevelEvaluator.evaluateRiskLevel()) {
            RiskLevel.LOW -> ContextCompat.getColor(context, R.color.risk_low)
            RiskLevel.MEDIUM -> ContextCompat.getColor(context, R.color.risk_medium)
            RiskLevel.HIGH -> ContextCompat.getColor(context, R.color.risk_high)
        }
    }
}