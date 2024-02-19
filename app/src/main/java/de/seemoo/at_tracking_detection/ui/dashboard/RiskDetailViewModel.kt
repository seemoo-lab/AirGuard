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
import de.seemoo.at_tracking_detection.database.repository.LocationRepository
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
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
    scanRepository: ScanRepository,
    val beaconRepository: BeaconRepository,
    val locationRepository: LocationRepository,
) : ViewModel() {

    private val relevantDate = RiskLevelEvaluator.relevantTrackingDateForRiskCalculation
    private val trackersFound: List<BaseDevice> = deviceRepository.trackingDevicesNotIgnoredSince(relevantDate)
    private val lastSeenDates = trackersFound.map {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(it.lastSeen)
    }

    var riskColor: Int
    val numberOfTrackersFound = deviceRepository.trackingDevicesNotIgnoredSinceCount(RiskLevelEvaluator.relevantTrackingDateForRiskCalculation).asLiveData()

    val totalLocationsTrackedCount= locationRepository.locationsSinceCount(relevantDate).asLiveData()

    // val discoveredBeacons: List<Beacon> = beaconRepository.getBeaconsForDevices(trackersFound)

    val totalNumberOfDevicesFound: LiveData<Int> = deviceRepository.countNotTracking.asLiveData()

    val isMapLoading = MutableLiveData(false)

    val receivedNotificationDatesString: String = lastSeenDates.joinToString(separator = "\n")

    val lastScans: String = run {
        val scans = scanRepository.relevantScans(false, 5)
        val scanDates = scans.map {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(it.endDate)
        }
        scanDates.joinToString(separator = "\n")
    }

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