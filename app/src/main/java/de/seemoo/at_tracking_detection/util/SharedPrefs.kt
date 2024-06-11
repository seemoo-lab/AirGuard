package de.seemoo.at_tracking_detection.util

import androidx.preference.PreferenceManager
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

object SharedPrefs {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ATTrackingDetectionApplication.getAppContext())

    var isScanningInBackground: Boolean
        get() {
            return sharedPreferences.getBoolean("isScanningInBackground", false)
        }
        set(value) {
            sharedPreferences.edit().putBoolean("isScanningInBackground", value).apply()
        }

    var useLocationInTrackingDetection: Boolean
        get() {
            return sharedPreferences.getBoolean("use_location", true)
        }
        set(value) {
            sharedPreferences.edit().putBoolean("use_location", value).apply()
        }

    var lastScanDate: LocalDateTime?
        get() {
            val dateString = sharedPreferences.getString("last_scan", null)
            if (dateString != null) {
                return try {
                    LocalDateTime.parse(dateString)
                }catch(e: DateTimeParseException) {
                    null
                }
            }
            return null
        }
        set(value) {
            sharedPreferences.edit().putString("last_scan", value.toString()).apply()
        }

    var nextScanDate: LocalDateTime?
        get() {
            val dateString = sharedPreferences.getString("next_scan", null)
            if (dateString != null) {
                return try {
                    LocalDateTime.parse(dateString)
                }catch(e: DateTimeParseException) {
                    null
                }
            }
            return null
        }
        set(value) {
            sharedPreferences.edit().putString("next_scan", value.toString()).apply()
        }

    var shareData: Boolean
        get() {
            return sharedPreferences.getBoolean("share_data", false)
        }
        set(value) {
            sharedPreferences.edit().putBoolean("share_data", value).apply()
        }

    var advancedMode: Boolean
        get() {
            return sharedPreferences.getBoolean("advanced_mode", false)
        }
        set(value) {
            sharedPreferences.edit().putBoolean("advanced_mode", value).apply()
        }

    var token: String?
        get() {
            return sharedPreferences.getString("token", null)
        }
        set(value) {
            sharedPreferences.edit().putString("token", value).apply()
        }

    var lastDataDonation: LocalDateTime?
        get() {
            val dateString = sharedPreferences.getString("lastDataDonation", null)
            if (dateString != null) {
                return try {
                    LocalDateTime.parse(dateString)
                }catch(e: DateTimeParseException) {
                    null
                }
            }
            return null
        }
        set(value) {
            sharedPreferences.edit().putString("lastDataDonation", value?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).apply()
        }

    var onBoardingCompleted: Boolean
        get() {
            return sharedPreferences.getBoolean("onboarding_completed", false)
        }
        set(value) {
            sharedPreferences.edit().putBoolean("onboarding_completed", value).apply()
        }

    var showOnboarding: Boolean
        get() {
            return sharedPreferences.getBoolean("show_onboarding", false)
        }
        set(value) {
            sharedPreferences.edit().putBoolean("show_onboarding", value).apply()
        }

    var useLowPowerBLEScan: Boolean
        get() {
            return sharedPreferences.getBoolean("use_low_power_ble", false)
        }
        set(value) {
            sharedPreferences.edit().putBoolean("use_low_power_ble", value).apply()
        }

    var lastTimeOpened: LocalDateTime?
        get() {
            val dateString = sharedPreferences.getString("last_time_opened", null)
            if (dateString != null) {
                return try {
                    LocalDateTime.parse(dateString)
                }catch(e: DateTimeParseException) {
                    null
                }
            }
            return null
        }
        set(value) {
            sharedPreferences.edit().putString("last_time_opened", value.toString()).apply()
        }

    var useMetricSystem: Boolean
        get() {
            val metricSystem = when (Locale.getDefault().country.uppercase()) {
                "US", "MM", "LR" -> false
                else -> true
            }
            return sharedPreferences.getBoolean("use_metric", metricSystem)
        }
        set(value) {
            sharedPreferences.edit().putBoolean("use_metric", value).apply()
        }

    var dismissSurveyInformation: Boolean
        get() {
            return sharedPreferences.getBoolean("dismiss_survey_information", false)
        }set(value) {
            sharedPreferences.edit().putBoolean("dismiss_survey_information", value).apply()
        }

    var surveyNotificationDate: LocalDateTime?
        get() {
            val dateString = sharedPreferences.getString("survey_notification_date", null)
            if (dateString != null) {
                return try {
                    LocalDateTime.parse(dateString)
                }catch(e: DateTimeParseException) {
                    null
                }
            }
            return null
        }
        set(value) {
            sharedPreferences.edit().putString("survey_notification_date", value?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).apply()
        }

    var surveyNotificationSent: Boolean
        get() {
            return sharedPreferences.getBoolean("survey_notification_sent", false)
        }set(value) {
        sharedPreferences.edit().putBoolean("survey_notification_sent", value).apply()
    }

    var riskSensitivity: String
        // 0: Low
        // 1: Medium
        // 2: High
        get() {
            return sharedPreferences.getString("risk_sensitivity", "medium")?:"medium"
        }
        set(value) {
            sharedPreferences.edit().putString("risk_sensitivity", value).apply()
        }

    var devicesFilter: Set<String>
        // 0: Low
        // 1: Medium
        // 2: High
        get() {
            return sharedPreferences.getStringSet("devices_filter", getDefaultDevicesFilterSet())?:getDefaultDevicesFilterSet()
        }
        set(value) {
            sharedPreferences.edit().putStringSet("devices_filter", value).apply()
        }

    var notificationPriorityHigh: Boolean
        get() {
            return sharedPreferences.getBoolean("notification_priority_high", true)
        }
        set(value) {
            sharedPreferences.edit().putBoolean("notification_priority_high", value).apply()
        }

    private fun getDefaultDevicesFilterSet(): Set<String> {
        val defaultValues = ATTrackingDetectionApplication.getAppContext().resources.getStringArray(R.array.devicesFilterValue)
        return defaultValues.toSet()
    }
}