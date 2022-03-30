package de.seemoo.at_tracking_detection.util

import android.content.Context
import android.content.SharedPreferences
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

object SharedPrefs {

    private val sharedPreferences = ATTrackingDetectionApplication.getCurrentActivity().getPreferences(
        Context.MODE_PRIVATE)

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
}