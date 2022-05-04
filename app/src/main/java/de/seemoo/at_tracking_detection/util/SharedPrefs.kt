package de.seemoo.at_tracking_detection.util

import android.content.Context
import android.content.SharedPreferences
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
                try {
                    return LocalDateTime.parse(dateString)
                }catch(e: DateTimeParseException) {
                    return null
                }
            }
            return null
        }
        set(value) {
            sharedPreferences.edit().putString("last_scan", value.toString()).apply()
        }

    var shareData: Boolean
        get() {
            return sharedPreferences.getBoolean("share_data", false)
        }
        set(value) {
            sharedPreferences.edit().putBoolean("share_data", value).apply()
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
                try {
                    return LocalDateTime.parse(dateString)
                }catch(e: DateTimeParseException) {
                    return null
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
                try {
                    return LocalDateTime.parse(dateString)
                }catch(e: DateTimeParseException) {
                    return null
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
                try {
                    return LocalDateTime.parse(dateString)
                }catch(e: DateTimeParseException) {
                    return null
                }
            }
            return null
        }
        set(value) {
            sharedPreferences.edit().putString("survey_notification_date", value?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).apply()
        }

    var surveyNotficationSent: Boolean
        get() {
            return sharedPreferences.getBoolean("survey_notification_sent", false)
        }set(value) {
        sharedPreferences.edit().putBoolean("survey_notification_sent", value).apply()
    }
}