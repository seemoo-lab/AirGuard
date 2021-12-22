package de.seemoo.at_tracking_detection.ui.dashboard

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.database.tables.Beacon
import de.seemoo.at_tracking_detection.database.tables.Device
import de.seemoo.at_tracking_detection.util.RiskLevel
import de.seemoo.at_tracking_detection.util.RiskLevelEvaluator
import kotlinx.coroutines.flow.count
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import javax.inject.Inject

@HiltViewModel
class RiskDetailViewModel @Inject constructor(
    application: Application,
    deviceRepository: DeviceRepository,
    beaconRepository: BeaconRepository,
) : androidx.lifecycle.AndroidViewModel(application) {

    val relevantDate = RiskLevelEvaluator.relevantTrackingDate()
    var riskColor: Int =  R.color.risk_low
    var numberOfTrackersFound: Int
    val totalLocationsTrackedCount: Int
    val dateFormat = DateFormat.getDateTimeInstance()

    val trackersFound: List<Device>
    val discoveredBeacons: List<Beacon>

    val receivedNotificationDatesString: String

    init {
        val ctx = getApplication<Application>()
        riskColor = when (RiskLevelEvaluator.evaluateRiskLevel(deviceRepository, beaconRepository)) {
            RiskLevel.LOW -> ctx.getColor(R.color.risk_low)
            RiskLevel.MEDIUM -> ctx.getColor(R.color.risk_medium)
            RiskLevel.HIGH -> ctx.getColor(R.color.risk_high)
        }

        val trackingDevices = deviceRepository.trackingDevicesSince(relevantDate)
        this.trackersFound = trackingDevices
        numberOfTrackersFound = trackingDevices.count()
        val beaconsFound = beaconRepository.getBeaconsForDevices(trackingDevices)
        this.discoveredBeacons = beaconsFound
        totalLocationsTrackedCount = beaconsFound.count()

        val lastSeenDates = trackingDevices.map {
            val date = Date.from(it.lastSeen.atZone(ZoneId.systemDefault()).toInstant())
            return@map dateFormat.format(date)
        }

        receivedNotificationDatesString = lastSeenDates.joinToString(separator = "\n")
    }
}