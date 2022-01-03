package de.seemoo.at_tracking_detection.ui.dashboard

import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.util.risk.RiskLevel
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class RiskCardViewModel @Inject constructor(
    riskLevelEvaluator: RiskLevelEvaluator,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    var riskLevel: String = "No risk"
    var riskColor: Int = 0
    var showLastDetection: Boolean = true
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

    init {
        lastScan = LocalDateTime.parse(
            sharedPreferences.getString(
                "last_scan",
                dateTime.toString()
            )
        )
        val context = ATTrackingDetectionApplication.getAppContext()
        val dateFormat = DateFormat.getDateTimeInstance()
        val lastDiscoveryDate = riskLevelEvaluator.getLastTrackerDiscoveryDate()
        val lastDiscoveryDateString = dateFormat.format(lastDiscoveryDate)
        val totalAlerts = riskLevelEvaluator.getNumberRelevantTrackers()
        val lastScanString =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(lastScan)

        lastUpdateModel = RiskRowViewModel(
            context.getString(R.string.last_scan_info, lastScanString),
            ContextCompat.getDrawable(context, R.drawable.ic_last_update)!!
        )
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)

        when (riskLevelEvaluator.evaluateRiskLevel()) {
            RiskLevel.LOW -> {
                riskLevel = context.getString(R.string.risk_level_low)
                riskColor = ContextCompat.getColor(context, R.color.risk_low)

                trackersFoundModel = RiskRowViewModel(
                    context.getString(R.string.no_trackers_found, RiskLevelEvaluator.RELEVANT_DAYS),
                    ContextCompat.getDrawable(context, R.drawable.ic_baseline_location_on_24)!!
                )
                lastDiscoveryModel = RiskRowViewModel(
                    context.getString(R.string.last_discovery),
                    ContextCompat.getDrawable(context, R.drawable.ic_clock)!!
                )

                showLastDetection = false

            }
            RiskLevel.MEDIUM -> {
                riskLevel = context.getString(R.string.risk_level_medium)
                riskColor = ContextCompat.getColor(context, R.color.risk_medium)

                trackersFoundModel = RiskRowViewModel(
                    context.getString(
                        R.string.found_x_trackers,
                        totalAlerts,
                        RiskLevelEvaluator.RELEVANT_DAYS
                    ),
                    ContextCompat.getDrawable(context, R.drawable.ic_baseline_location_on_24)!!
                )

                lastDiscoveryModel = RiskRowViewModel(
                    context.getString(R.string.last_discovery, lastDiscoveryDateString),
                    ContextCompat.getDrawable(context, R.drawable.ic_clock)!!
                )


            }
            else -> {
                //High risk
                riskLevel = context.getString(R.string.risk_level_high)
                riskColor = ContextCompat.getColor(context, R.color.risk_high)


                trackersFoundModel = RiskRowViewModel(
                    context.getString(
                        R.string.found_x_trackers,
                        totalAlerts,
                        RiskLevelEvaluator.RELEVANT_DAYS
                    ),
                    ContextCompat.getDrawable(context, R.drawable.ic_baseline_location_on_24)!!
                )

                lastDiscoveryModel = RiskRowViewModel(
                    context.getString(R.string.last_discovery, lastDiscoveryDateString),
                    ContextCompat.getDrawable(context, R.drawable.ic_clock)!!
                )
            }
        }
    }
}