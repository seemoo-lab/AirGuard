package de.seemoo.at_tracking_detection.ui.dashboard

import android.app.Application
import android.content.SharedPreferences
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.asLiveData
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.database.tables.Notification
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import de.seemoo.at_tracking_detection.worker.WorkerConstants
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

@HiltViewModel
class RiskCardViewModel @Inject constructor(
    application: Application,
    notificationRepository: NotificationRepository,
    private val sharedPreferences: SharedPreferences,
    backgroundWorkScheduler: BackgroundWorkScheduler,
) : androidx.lifecycle.AndroidViewModel(application) {

    var riskLevel: String = "No risk"
    var riskColor: Int
    var showLastDetection: Boolean = false
    var clickable: Boolean = true
    var trackersFoundModel: RiskRowViewModel
    var lastUpdateModel: RiskRowViewModel
    var lastDiscoveryModel: RiskRowViewModel

    val alertsLast14DaysCount: LiveData<Int> = notificationRepository.totalCountChange(LocalDateTime.now().minusDays(14)).asLiveData()
    val lastNotification: LiveData<List<Notification>> = notificationRepository.last_notification.asLiveData()
    val allNotificationsLast14Days: LiveData<List<Notification>> = notificationRepository.notificationsSince(LocalDateTime.now().minusDays(14)).asLiveData()


    private var dateTime: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
    private var lastScan: LocalDateTime
    private var sharedPreferencesListener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "last_scan" -> lastScan = LocalDateTime.parse(
                    sharedPreferences.getString(
                        "last_scan",
                        dateTime.minusMinutes(15).toString()
                    )
                )
            }
        }

    val isScanning: LiveData<Boolean> =
        Transformations.map(backgroundWorkScheduler.getState(WorkerConstants.PERIODIC_SCAN_WORKER)) {
            it == WorkInfo.State.RUNNING
        }



    init {
        val lastScan = LocalDateTime.parse(
            sharedPreferences.getString(
                "last_scan",
                dateTime.toString()
            )
        )
        this.lastScan = lastScan
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)

        val totalAlerts = alertsLast14DaysCount.value ?: 0
        val notifications = allNotificationsLast14Days.value ?: ArrayList()
        val dateFormat = DateFormat.getDateTimeInstance()
        val context = getApplication<Application>()

        val lastDiscoveryDate = lastNotification.value?.let {
            it.firstOrNull()?.let { Date.from(it.createdAt.atZone(ZoneId.systemDefault()).toInstant()) }
        } ?: Date()
        val lastDiscoveryDateString = dateFormat.format(lastDiscoveryDate)
        val lastScanDate: Date = Date.from(lastScan.atZone(ZoneId.systemDefault()).toInstant())
        val lastScanString = dateFormat.format(lastScanDate)
        showLastDetection = true


        lastUpdateModel = RiskRowViewModel(
            context.getString(R.string.last_scan_info, lastScanString),
            AppCompatResources.getDrawable(context,R.drawable.ic_last_update)!!
        )


        if (totalAlerts == 0) {
            riskLevel = context.getString(R.string.risk_level_low)
            riskColor = context.getColor(R.color.risk_low)

            trackersFoundModel = RiskRowViewModel(
                context.getString(R.string.no_trackers_found),
                AppCompatResources.getDrawable(context,R.drawable.ic_baseline_location_on_24)!!
            )
            lastDiscoveryModel = RiskRowViewModel(
                context.getString(R.string.last_discovery),
                AppCompatResources.getDrawable(context,R.drawable.ic_clock)!!
            )

            showLastDetection = false

        } else if (totalAlerts == 1) {
            riskLevel = context.getString(R.string.risk_level_medium)
            riskColor = context.getColor(R.color.risk_medium)

            trackersFoundModel = RiskRowViewModel(
                context.getString(R.string.found_x_trackers, totalAlerts),
                AppCompatResources.getDrawable(context,R.drawable.ic_baseline_location_on_24)!!
            )

            lastDiscoveryModel = RiskRowViewModel(
                context.getString(R.string.last_discovery, lastDiscoveryDateString),
                AppCompatResources.getDrawable(context,R.drawable.ic_clock)!!
            )


        } else {
            //Check if those notifications where on different days
            val firstNotif = notifications.first()
            val lastNotif = notifications.last()

            val daysDiff = firstNotif.createdAt.until(lastNotif.createdAt, ChronoUnit.DAYS)
            if (daysDiff >= 1) {
                //High risk
                riskLevel = context.getString(R.string.risk_level_high)
                riskColor = context.getColor(R.color.risk_high)
            } else {
                riskLevel = context.getString(R.string.risk_level_medium)
                riskColor = context.getColor(R.color.risk_medium)
            }

            trackersFoundModel = RiskRowViewModel(
                context.getString(R.string.found_x_trackers, totalAlerts),
                AppCompatResources.getDrawable(context,R.drawable.ic_baseline_location_on_24)!!
            )

            lastDiscoveryModel = RiskRowViewModel(
                context.getString(R.string.last_discovery, lastDiscoveryDateString),
                AppCompatResources.getDrawable(context,R.drawable.ic_clock)!!
            )
        }

    }
}