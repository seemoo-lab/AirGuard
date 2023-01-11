package de.seemoo.at_tracking_detection.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.util.SharedPrefs
import timber.log.Timber

class ScheduleWorkersReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("Broadcast received ${intent?.action}")
        val backgroundWorkScheduler = ATTrackingDetectionApplication.getCurrentApp()?.backgroundWorkScheduler
        //Enqueue the scan task
        backgroundWorkScheduler?.launch()
        if (SharedPrefs.shareData) {
            backgroundWorkScheduler?.scheduleShareData()
        }
        BackgroundWorkScheduler.scheduleAlarmWakeupIfScansFail()
        Timber.d("Scheduled background work")

        val service = Intent(context, ForegroundService::class.java)
        context!!.startForegroundService(service)

        ATTrackingDetectionApplication.getCurrentApp()?.notificationService?.scheduleSurveyNotification(false)
    }
}