package de.seemoo.at_tracking_detection.util

import androidx.preference.PreferenceManager
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import androidx.core.content.edit

object SharedPrefs {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ATTrackingDetectionApplication.getAppContext())

    init {
        // Migrate old devices filter to new format
        migrateDevicesFilter()
    }

    private fun migrateDevicesFilter() {
        val oldDevicesFilterKey = "devices_filter"
        val newDevicesFilterKey = "devices_filter_unselected"
        if (sharedPreferences.contains(oldDevicesFilterKey) && !sharedPreferences.contains(newDevicesFilterKey)) {
            var oldSelectedOptions = sharedPreferences.getStringSet(oldDevicesFilterKey, emptySet()) ?: emptySet()

            val googleFindMyNetworkValue = ATTrackingDetectionApplication.getAppContext().resources.getStringArray(R.array.devicesFilterValue).find { it == "google_find_my_network" }
            googleFindMyNetworkValue?.let {
                oldSelectedOptions = oldSelectedOptions + it
            }

            val pebbleBeeValue = ATTrackingDetectionApplication.getAppContext().resources.getStringArray(R.array.devicesFilterValue).find { it == "pebblebees" }
            pebbleBeeValue?.let {
                oldSelectedOptions = oldSelectedOptions + it
            }

            val allOptions = getAllDevicesFilterOptions()
            val newUnselectedOptions = allOptions - oldSelectedOptions
            sharedPreferences.edit { putStringSet(newDevicesFilterKey, newUnselectedOptions) }
            sharedPreferences.edit { remove(oldDevicesFilterKey) }
        }
    }

    var isScanningInBackground: Boolean
        get() {
            return sharedPreferences.getBoolean("isScanningInBackground", false)
        }
        set(value) {
            sharedPreferences.edit { putBoolean("isScanningInBackground", value) }
        }

    var deactivateBackgroundScanning: Boolean
        get() {
            return sharedPreferences.getBoolean("deactivate_background_scanning", false)
        }
        set(value) {
            sharedPreferences.edit { putBoolean("deactivate_background_scanning", value) }
        }

    var useLocationInTrackingDetection: Boolean
        get() {
            return sharedPreferences.getBoolean("use_location", true)
        }
        set(value) {
            sharedPreferences.edit { putBoolean("use_location", value) }
        }

    var lastScanDate: LocalDateTime?
        get() {
            val dateString = sharedPreferences.getString("last_scan", null)
            if (dateString != null) {
                return try {
                    LocalDateTime.parse(dateString)
                }catch(_: DateTimeParseException) {
                    null
                }
            }
            return null
        }
        set(value) {
            sharedPreferences.edit { putString("last_scan", value.toString()) }
        }

    var nextScanDate: LocalDateTime?
        get() {
            val dateString = sharedPreferences.getString("next_scan", null)
            if (dateString != null) {
                return try {
                    LocalDateTime.parse(dateString)
                }catch(_: DateTimeParseException) {
                    null
                }
            }
            return null
        }
        set(value) {
            sharedPreferences.edit { putString("next_scan", value.toString()) }
        }

    var shareData: Boolean
        get() {
            return sharedPreferences.getBoolean("share_data", false)
        }
        set(value) {
            sharedPreferences.edit { putBoolean("share_data", value) }
        }

    var advancedMode: Boolean
        get() {
            return sharedPreferences.getBoolean("advanced_mode", false)
        }
        set(value) {
            sharedPreferences.edit { putBoolean("advanced_mode", value) }
        }

    var token: String?
        get() {
            return sharedPreferences.getString("token", null)
        }
        set(value) {
            sharedPreferences.edit { putString("token", value) }
        }

    var lastDataDonation: LocalDateTime?
        get() {
            val dateString = sharedPreferences.getString("lastDataDonation", null)
            if (dateString != null) {
                return try {
                    LocalDateTime.parse(dateString)
                }catch(_: DateTimeParseException) {
                    null
                }
            }
            return null
        }
        set(value) {
            sharedPreferences.edit {
                putString(
                    "lastDataDonation",
                    value?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
            }
        }

    var onBoardingCompleted: Boolean
        get() {
            return sharedPreferences.getBoolean("onboarding_completed", false)
        }
        set(value) {
            sharedPreferences.edit { putBoolean("onboarding_completed", value) }
        }

    var showOnboarding: Boolean
        get() {
            return sharedPreferences.getBoolean("show_onboarding", false)
        }
        set(value) {
            sharedPreferences.edit { putBoolean("show_onboarding", value) }
        }

    var useLowPowerBLEScan: Boolean
        get() {
            return sharedPreferences.getBoolean("use_low_power_ble", false)
        }
        set(value) {
            sharedPreferences.edit { putBoolean("use_low_power_ble", value) }
        }

    var lastTimeOpened: LocalDateTime?
        get() {
            val dateString = sharedPreferences.getString("last_time_opened", null)
            if (dateString != null) {
                return try {
                    LocalDateTime.parse(dateString)
                }catch(_: DateTimeParseException) {
                    null
                }
            }
            return null
        }
        set(value) {
            sharedPreferences.edit { putString("last_time_opened", value.toString()) }
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
            sharedPreferences.edit { putBoolean("use_metric", value) }
        }

    var dismissSurveyInformation: Boolean
        get() {
            return sharedPreferences.getBoolean("dismiss_survey_information", false)
        }set(value) {
            sharedPreferences.edit { putBoolean("dismiss_survey_information", value) }
        }

    var showSamsungAndroid15BugNotification: Boolean
        get() {
            return sharedPreferences.getBoolean("samsung_bug_notification", false)
        }set(value) {
        sharedPreferences.edit { putBoolean("samsung_bug_notification", value) }
    }

    var showMissingNotificationPermissionWarning: Boolean
        get() {
            return sharedPreferences.getBoolean("missing_notification_permission_warning", false)
        }set(value) {
        sharedPreferences.edit { putBoolean("missing_notification_permission_warning", value) }
    }

    var showGenericBluetoothBugNotification: Boolean
        get() {
            return sharedPreferences.getBoolean("generic_bluetooth_bug_notification", false)
        }set(value) {
        sharedPreferences.edit { putBoolean("generic_bluetooth_bug_notification", value) }
    }

    var surveyNotificationDate: LocalDateTime?
        get() {
            val dateString = sharedPreferences.getString("survey_notification_date", null)
            if (dateString != null) {
                return try {
                    LocalDateTime.parse(dateString)
                }catch(_: DateTimeParseException) {
                    null
                }
            }
            return null
        }
        set(value) {
            sharedPreferences.edit {
                putString(
                    "survey_notification_date",
                    value?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
            }
        }

    var surveyNotificationSent: Boolean
        get() {
            return sharedPreferences.getBoolean("survey_notification_sent", false)
        }set(value) {
        sharedPreferences.edit { putBoolean("survey_notification_sent", value) }
    }

    var riskSensitivity: String
        // 0: Low
        // 1: Medium
        // 2: High
        get() {
            return sharedPreferences.getString("risk_sensitivity", "medium")?:"medium"
        }
        set(value) {
            sharedPreferences.edit { putString("risk_sensitivity", value) }
        }

    var appOpenCount: Int
        // How often the app has been opened
        get() {
            return sharedPreferences.getInt("app_open_count", 0)
        }
        set(value) {
            sharedPreferences.edit { putInt("app_open_count", value) }
        }

    var reviewShown: Boolean
        // If the review dialog has been shown
        get() {
            return sharedPreferences.getBoolean("review_shown", false)
        }
        set(value) {
            sharedPreferences.edit { putBoolean("review_shown", value) }
        }

    var devicesFilter: Set<String>
        get() {
            val allOptions = getAllDevicesFilterOptions()
            val selectedOptions = sharedPreferences.getStringSet("devices_filter_unselected", emptySet())?: emptySet()
            return allOptions - selectedOptions
        }
        set(value) {
            val allOptions = getAllDevicesFilterOptions()
            val unselectedOptions = allOptions - value
            sharedPreferences.edit {
                putStringSet(
                    "devices_filter_unselected",
                    unselectedOptions
                )
            }
        }

    var notificationPriorityHigh: Boolean
        get() {
            return sharedPreferences.getBoolean("notification_priority_high", true)
        }
        set(value) {
            sharedPreferences.edit { putBoolean("notification_priority_high", value) }
        }

    var sendBLEErrorMessages: Boolean
        get() {
            return sharedPreferences.getBoolean("send_ble_error_messages", true)
        }
        set(value) {
            sharedPreferences.edit { putBoolean("send_ble_error_messages", value) }
        }

    var usePermanentBluetoothScanner: Boolean
        get() {
            return sharedPreferences.getBoolean("use_permanent_bluetooth_scanner", false)
        }
        set(value) {
            sharedPreferences.edit { putBoolean("use_permanent_bluetooth_scanner", value) }
        }

    private fun getAllDevicesFilterOptions(): Set<String> {
        val allOptions = ATTrackingDetectionApplication.getAppContext().resources.getStringArray(R.array.devicesFilterValue)
        return allOptions.toSet()
    }
}