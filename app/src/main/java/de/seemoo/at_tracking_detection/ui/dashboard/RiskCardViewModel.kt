package de.seemoo.at_tracking_detection.ui.dashboard

import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.risk.RiskLevel
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class RiskCardViewModel @Inject constructor(
    private val riskLevelEvaluator: RiskLevelEvaluator,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    var riskLevel: String = "No risk"
    var riskColor: Int = 0
    var showLastDetection: Boolean = true
    var clickable: Boolean = true
    var trackersFoundModel: MutableLiveData<RiskRowViewModel> = MutableLiveData()
    var lastUpdateModel: MutableLiveData<RiskRowViewModel> = MutableLiveData()
    var lastDiscoveryModel: MutableLiveData<RiskRowViewModel> = MutableLiveData()
    var dismissSurveyInformation: MutableLiveData<Boolean> = MutableLiveData(SharedPrefs.dismissSurveyInformation)

    private var lastScan: LocalDateTime? = null
    private var sharedPreferencesListener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "last_scan" -> {
                    lastScan = SharedPrefs.lastScanDate
                    updateLastUpdateModel()
                    }
                }

            when (key) {
                "dismiss_survey_information" -> {
                    dismissSurveyInformation.postValue(SharedPrefs.dismissSurveyInformation)
                }
            }
            }


    init {
        updateRiskLevel()
    }

    fun updateLastUpdateModel() {
        val context = ATTrackingDetectionApplication.getAppContext()

        val lastScanString = if (lastScan != null) {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(lastScan)
        }else {
            ATTrackingDetectionApplication.getAppContext().getString(R.string.none)
        }

        lastUpdateModel.postValue ( RiskRowViewModel(
            context.getString(R.string.last_scan_info, lastScanString),
            ContextCompat.getDrawable(context, R.drawable.ic_last_update)!!
        ))
    }

    fun updateRiskLevel() {
        lastScan = SharedPrefs.lastScanDate
        val context = ATTrackingDetectionApplication.getAppContext()
        val dateFormat = DateFormat.getDateTimeInstance()
        val lastDiscoveryDate = riskLevelEvaluator.getLastTrackerDiscoveryDate()
        val lastDiscoveryDateString = dateFormat.format(lastDiscoveryDate)
        val totalAlerts = riskLevelEvaluator.getNumberRelevantTrackers()

        updateLastUpdateModel()

        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)

        when (riskLevelEvaluator.evaluateRiskLevel()) {
            RiskLevel.LOW -> {
                riskLevel = context.getString(R.string.risk_level_low)
                riskColor = ContextCompat.getColor(context, R.color.risk_low)

                trackersFoundModel.postValue(RiskRowViewModel(
                    context.getString(R.string.no_trackers_found, RiskLevelEvaluator.RELEVANT_HOURS),
                    ContextCompat.getDrawable(context, R.drawable.ic_baseline_location_on_24)!!
                ))
                lastDiscoveryModel.postValue(RiskRowViewModel(
                    context.getString(R.string.last_discovery),
                    ContextCompat.getDrawable(context, R.drawable.ic_clock)!!
                ))

                showLastDetection = false

            }
            RiskLevel.MEDIUM -> {
                riskLevel = context.getString(R.string.risk_level_medium)
                riskColor = ContextCompat.getColor(context, R.color.risk_medium)

                trackersFoundModel.postValue(RiskRowViewModel(
                    context.getString(
                        R.string.found_x_trackers,
                        totalAlerts,
                        RiskLevelEvaluator.RELEVANT_HOURS
                    ),
                    ContextCompat.getDrawable(context, R.drawable.ic_baseline_location_on_24)!!
                ))

                lastDiscoveryModel.postValue(RiskRowViewModel(
                    context.getString(R.string.last_discovery, lastDiscoveryDateString),
                    ContextCompat.getDrawable(context, R.drawable.ic_clock)!!
                ))


            }
            else -> {
                //High risk
                riskLevel = context.getString(R.string.risk_level_high)
                riskColor = ContextCompat.getColor(context, R.color.risk_high)


                trackersFoundModel.postValue(RiskRowViewModel(
                    context.getString(
                        R.string.found_x_trackers,
                        totalAlerts,
                        RiskLevelEvaluator.RELEVANT_HOURS
                    ),
                    ContextCompat.getDrawable(context, R.drawable.ic_baseline_location_on_24)!!
                ))

                lastDiscoveryModel.postValue(RiskRowViewModel(
                    context.getString(R.string.last_discovery, lastDiscoveryDateString),
                    ContextCompat.getDrawable(context, R.drawable.ic_clock)!!
                ))
            }
        }
    }
}