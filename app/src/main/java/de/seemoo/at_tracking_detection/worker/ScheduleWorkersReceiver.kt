package de.seemoo.at_tracking_detection.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.util.SharedPrefs
import timber.log.Timber
import java.util.concurrent.TimeUnit

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

        ATTrackingDetectionApplication.getCurrentApp()?.notificationService?.scheduleSurveyNotification(false)
    }



    companion object {
        const val OBSERVATION_DURATION = 1L // in hours
        const val OBSERVATION_DELTA = 30L // in minutes

        fun scheduleWorker(context: Context, deviceAddress: String) {
            val inputData = Data.Builder()
                .putString(ScheduleWorkersWorker.DEVICE_ADDRESS_PARAM, deviceAddress)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ScheduleWorkersWorker>()
                .setInputData(inputData)
                .setInitialDelay(OBSERVATION_DURATION, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}