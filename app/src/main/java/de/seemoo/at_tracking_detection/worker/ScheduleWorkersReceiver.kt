package de.seemoo.at_tracking_detection.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner
import de.seemoo.at_tracking_detection.detection.ScanBluetoothWorker
import de.seemoo.at_tracking_detection.util.SharedPrefs
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

class ScheduleWorkersReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("Broadcast received ${intent?.action}")

        if (intent?.action == "AlarmManagerWakeUp_Schedule_BackgroundScan") {
            // The app has been launched because no scan was performed since two hours
            val backgroundWorkScheduler = ATTrackingDetectionApplication.getCurrentApp().backgroundWorkScheduler
            //Schedule the periodic scan worker which runs every 15min
            backgroundWorkScheduler.launch()
            if (SharedPrefs.shareData) {
                backgroundWorkScheduler.scheduleShareData()
            }
            BackgroundWorkScheduler.scheduleAlarmWakeupIfScansFail()
        }else {
            // action = AlarmManagerWakeUp_Perform_BackgroundScan
            // The app has been launched to perform another scan
            BackgroundWorkScheduler.scheduleScanWithAlarm()
            val app = ATTrackingDetectionApplication.getCurrentApp()
            if (app != null) {

                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch {
                    Timber.d("Running scan launched from Alert")
                    BackgroundBluetoothScanner.scanInBackground(startedFrom = "ScheduleWorkersReceiver")
                }

            }else {
                Timber.d("Could not find required dependencies")
            }
        }
    }



    companion object {
        const val OBSERVATION_DURATION = 1L // in hours
        const val OBSERVATION_DELTA = 30L // in minutes

        fun scheduleWorker(context: Context, deviceAddress: String) {
            val inputData = Data.Builder()
                .putString(ObserveTrackerWorker.DEVICE_ADDRESS_PARAM, deviceAddress)
                .build()

            val workRequestObserveTracker = OneTimeWorkRequestBuilder<ObserveTrackerWorker>()
                .setInputData(inputData)
                .setInitialDelay(OBSERVATION_DURATION, TimeUnit.HOURS)
                .build()

            val workRequestBluetoothScan = OneTimeWorkRequestBuilder<ScanBluetoothWorker>()
                .setInitialDelay(OBSERVATION_DURATION*60-5, TimeUnit.MINUTES) // make a scan 5 minutes before the observation ends
                .build()

            WorkManager.getInstance(context).enqueue(workRequestBluetoothScan)
            WorkManager.getInstance(context).enqueue(workRequestObserveTracker)
        }

    }
}

