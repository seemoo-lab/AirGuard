package de.seemoo.at_tracking_detection.worker

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import timber.log.Timber

class SetExactAlarmPermissionChangedReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            Timber.d("Received AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")
            ATTrackingDetectionApplication.getCurrentApp().backgroundWorkScheduler.launch()
        }
    }
}