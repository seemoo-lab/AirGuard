package de.seemoo.at_tracking_detection.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.util.SharedPrefs
import timber.log.Timber
import javax.inject.Inject

class ScheduleWorkersReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("Broadcast received ${intent?.action}")
        val backgroundWorkScheduler = ATTrackingDetectionApplication.getCurrentApp().backgroundWorkScheduler
        //Enqueue the scan task
        backgroundWorkScheduler.launch()
        if (SharedPrefs.shareData) {
            backgroundWorkScheduler.scheduleShareData()
        }
        Timber.d("Scheduled background work")
    }
}