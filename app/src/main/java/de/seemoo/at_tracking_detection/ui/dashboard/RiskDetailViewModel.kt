package de.seemoo.at_tracking_detection.ui.dashboard

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.util.RiskLevel
import de.seemoo.at_tracking_detection.util.RiskLevelEvaluator
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import javax.inject.Inject

@HiltViewModel
class RiskDetailViewModel @Inject constructor(
    application: Application,
    notificationRepository: NotificationRepository,
    private val beaconRepository: BeaconRepository,
) : androidx.lifecycle.AndroidViewModel(application) {

    val relevantDate = LocalDateTime.now().minusDays(RiskLevelEvaluator.RELEVANT_DAYS)
    var riskColor: Int =  R.color.risk_low
    var numberOfTrackersFound: LiveData<Int> = notificationRepository.totalCountChange(relevantDate).asLiveData()
    val totalLocationsTrackedCount: LiveData<Int> = beaconRepository.totalLocationCountChange(relevantDate).asLiveData()
    val dateFormat = DateFormat.getDateTimeInstance()

    val receivedNotificationDatesStrings: List<String> =  notificationRepository.notificationsSince(relevantDate).map {
        val date = Date.from(it.createdAt.atZone(ZoneId.systemDefault()).toInstant())
        return@map dateFormat.format(date)
    }

    val receivedNotificationDatesString: String = receivedNotificationDatesStrings.joinToString(separator = ", \n")

    init {
        val ctx = getApplication<Application>()
        riskColor = when (RiskLevelEvaluator.evaluateRiskLevel(notificationRepository)) {
            RiskLevel.LOW -> ctx.getColor(R.color.risk_low)
            RiskLevel.MEDIUM -> ctx.getColor(R.color.risk_medium)
            RiskLevel.HIGH -> ctx.getColor(R.color.risk_high)
        }
    }
}