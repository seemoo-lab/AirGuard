package de.seemoo.at_tracking_detection.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.LocationRepository
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.util.risk.RiskLevel
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class RiskDetailViewModel @Inject constructor(
    private val riskLevelEvaluator: RiskLevelEvaluator,
    private val deviceRepository: DeviceRepository,
    private val scanRepository: ScanRepository,
    val beaconRepository: BeaconRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val relevantDate = RiskLevelEvaluator.relevantTrackingDateForRiskCalculation

    val riskColor = MutableLiveData<Int>(R.color.risk_low)

    val numberOfTrackersFound = deviceRepository.trackingDevicesNotIgnoredSinceCount(RiskLevelEvaluator.relevantTrackingDateForRiskCalculation).asLiveData()

    val totalLocationsTrackedCount = locationRepository.locationsSinceCount(relevantDate).asLiveData()

    val totalNumberOfDevicesFound: LiveData<Int> = deviceRepository.countNotTracking.asLiveData()

    val isMapLoading = MutableLiveData(false)

    val receivedNotificationDatesString = MutableLiveData<String>("")

    val lastScans = MutableLiveData<String>("")

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val trackersFound: List<BaseDevice> = deviceRepository.trackingDevicesNotIgnoredSince(relevantDate)
            val lastSeenDates = trackersFound.map {
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(it.lastSeen)
            }
            receivedNotificationDatesString.postValue(lastSeenDates.joinToString(separator = "\n"))

            val scans = scanRepository.relevantScans(false, 5)
            val scanDates = scans.map {
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(it.endDate)
            }
            lastScans.postValue(scanDates.joinToString(separator = "\n"))

            val evaluatedRiskColor = when (riskLevelEvaluator.evaluateRiskLevel()) {
                RiskLevel.LOW -> R.color.risk_low
                RiskLevel.MEDIUM -> R.color.risk_medium
                RiskLevel.HIGH -> R.color.risk_high
            }
            riskColor.postValue(evaluatedRiskColor)
            Timber.d("Risk Color ID: $evaluatedRiskColor")
        }
    }
}
