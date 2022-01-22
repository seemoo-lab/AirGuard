package de.seemoo.at_tracking_detection.ui.dashboard

import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.util.risk.RiskLevel
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class RiskDetailViewModel @Inject constructor(
    riskLevelEvaluator: RiskLevelEvaluator,
    deviceRepository: DeviceRepository,
    val beaconRepository: BeaconRepository
) : ViewModel() {

    private val relevantDate = RiskLevelEvaluator.relevantTrackingDate
    private val trackersFound: List<BaseDevice> = deviceRepository.trackingDevicesSince(relevantDate)
    private val lastSeenDates = trackersFound.map {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(it.lastSeen)
    }

    var riskColor: Int
    var numberOfTrackersFound: Int = trackersFound.count()
    val totalLocationsTrackedCount: Int =
        beaconRepository.getBeaconsForDevices(trackersFound).count()
    val discoveredBeacons: List<Beacon> = beaconRepository.getBeaconsForDevices(trackersFound)

    //Total numbers
    val totalNumberOfBeaconsFound: LiveData<Int> = beaconRepository.totalBeaconCountChange(relevantDate).asLiveData()
    val totalNumberOfDevicesFound: LiveData<Int> = deviceRepository.deviceCountSince(relevantDate).asLiveData()


    val isMapLoading = MutableLiveData(false)

    val receivedNotificationDatesString: String = lastSeenDates.joinToString(separator = "\n")

    fun allBeacons(): Flow<List<Beacon>> {
        return beaconRepository.getBeaconsSince(relevantDate)
    }

    init {
        val context = ATTrackingDetectionApplication.getAppContext()
        riskColor = when (riskLevelEvaluator.evaluateRiskLevel()) {
            RiskLevel.LOW -> ContextCompat.getColor(context, R.color.risk_low)
            RiskLevel.MEDIUM -> ContextCompat.getColor(context, R.color.risk_medium)
            RiskLevel.HIGH -> ContextCompat.getColor(context, R.color.risk_high)
        }
        Timber.d("Risk Color: $riskColor")
    }
}