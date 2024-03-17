package de.seemoo.at_tracking_detection.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.seemoo.at_tracking_detection.util.SharedPrefs
import timber.log.Timber

class ScheduledNotificationReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("Broadcast received ${intent?.action}")

        // val notificationService = ATTrackingDetectionApplication.getCurrentApp()?.notificationService
        SharedPrefs.dismissSurveyInformation = true
    }
}