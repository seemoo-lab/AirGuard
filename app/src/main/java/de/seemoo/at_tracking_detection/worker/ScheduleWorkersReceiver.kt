package de.seemoo.at_tracking_detection.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner
import de.seemoo.at_tracking_detection.util.SharedPrefs
import timber.log.Timber
import java.util.concurrent.TimeUnit

class ScheduleWorkersReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("Broadcast received ${intent?.action}")

        if (intent?.action == "AlarmManagerWakeUp_Schedule_BackgroundScan") { // DO NOT REMOVE THIS
            // The app has been launched because no scan was performed since two hours
            val backgroundWorkScheduler = ATTrackingDetectionApplication.getCurrentApp().backgroundWorkScheduler
            // Schedule the periodic scan worker which runs every 15min
            backgroundWorkScheduler.launch()
            if (SharedPrefs.shareData) {
                backgroundWorkScheduler.scheduleShareData()
            }
            BackgroundWorkScheduler.scheduleAlarmWakeupIfScansFail()
        } else {
            // action = AlarmManagerWakeUp_Perform_BackgroundScan
            // The app has been launched to perform another scan
            BackgroundWorkScheduler.scheduleScanWithAlarm()
            Timber.d("Running scan launched from Alert")
            BackgroundBluetoothScanner.startScanInBackground(startedFrom = "ScheduleWorkersReceiver")
        }
    }



    companion object {
        const val OBSERVATION_DURATION = 1L // in hours
        const val OBSERVATION_DELTA = 30L // in minutes

        // TODO: replace ObserveTracker
        fun scheduleWorker(context: Context, deviceAddress: String) {
            val inputData = Data.Builder()
                .putString(ObserveTrackerWorker.DEVICE_ADDRESS_PARAM, deviceAddress)
                .build()

            val workRequestObserveTracker = OneTimeWorkRequestBuilder<ObserveTrackerWorker>()
                .setInputData(inputData)
                .setInitialDelay(OBSERVATION_DURATION, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueue(workRequestObserveTracker)
        }

    }
}

