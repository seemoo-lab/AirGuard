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
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.database.tables.Notification
import de.seemoo.at_tracking_detection.util.RiskLevel
import de.seemoo.at_tracking_detection.util.RiskLevelEvaluator
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
    deviceRepository: DeviceRepository,
    beaconRepository: BeaconRepository,
    private val sharedPreferences: SharedPreferences,
    backgroundWorkScheduler: BackgroundWorkScheduler,
) : androidx.lifecycle.AndroidViewModel(application) {

    var riskLevel: String = "No risk"
    var riskColor: Int = R.color.risk_low
    var showLastDetection: Boolean = false
    var clickable: Boolean = true
    var trackersFoundModel: RiskRowViewModel
    var lastUpdateModel: RiskRowViewModel
    var lastDiscoveryModel: RiskRowViewModel

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

        val dateFormat = DateFormat.getDateTimeInstance()
        val context = getApplication<Application>()

        val lastDiscoveryDate = RiskLevelEvaluator.getLastTrackerDiscoveryDate(deviceRepository)
        val lastDiscoveryDateString = dateFormat.format(lastDiscoveryDate)
        val lastScanDate: Date = Date.from(lastScan.atZone(ZoneId.systemDefault()).toInstant())
        val lastScanString = dateFormat.format(lastScanDate)
        showLastDetection = true


        lastUpdateModel = RiskRowViewModel(
            context.getString(R.string.last_scan_info, lastScanString),
            AppCompatResources.getDrawable(context,R.drawable.ic_last_update)!!
        )

        val risk = RiskLevelEvaluator.evaluateRiskLevel(deviceRepository, beaconRepository)
        val totalAlerts = RiskLevelEvaluator.getNumberRelevantTrackers(deviceRepository)

        if (risk == RiskLevel.LOW) {
            riskLevel = context.getString(R.string.risk_level_low)
            riskColor = context.getColor(R.color.risk_low)

            trackersFoundModel = RiskRowViewModel(
                context.getString(R.string.no_trackers_found, RiskLevelEvaluator.RELEVANT_DAYS),
                AppCompatResources.getDrawable(context,R.drawable.ic_baseline_location_on_24)!!
            )
            lastDiscoveryModel = RiskRowViewModel(
                context.getString(R.string.last_discovery),
                AppCompatResources.getDrawable(context,R.drawable.ic_clock)!!
            )

            showLastDetection = false

        } else if (risk == RiskLevel.MEDIUM) {
            riskLevel = context.getString(R.string.risk_level_medium)
            riskColor = context.getColor(R.color.risk_medium)

            trackersFoundModel = RiskRowViewModel(
                context.getString(R.string.found_x_trackers, totalAlerts, RiskLevelEvaluator.RELEVANT_DAYS),
                AppCompatResources.getDrawable(context,R.drawable.ic_baseline_location_on_24)!!
            )

            lastDiscoveryModel = RiskRowViewModel(
                context.getString(R.string.last_discovery, lastDiscoveryDateString),
                AppCompatResources.getDrawable(context,R.drawable.ic_clock)!!
            )


        } else {
            //High risk
            riskLevel = context.getString(R.string.risk_level_high)
            riskColor = context.getColor(R.color.risk_high)


            trackersFoundModel = RiskRowViewModel(
                context.getString(
                    R.string.found_x_trackers,
                    totalAlerts,
                    RiskLevelEvaluator.RELEVANT_DAYS
                ),
                AppCompatResources.getDrawable(context, R.drawable.ic_baseline_location_on_24)!!
            )

            lastDiscoveryModel = RiskRowViewModel(
                context.getString(R.string.last_discovery, lastDiscoveryDateString),
                AppCompatResources.getDrawable(context, R.drawable.ic_clock)!!
            )
        }
    }
}