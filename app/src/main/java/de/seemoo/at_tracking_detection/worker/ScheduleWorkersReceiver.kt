package de.seemoo.at_tracking_detection.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner
import de.seemoo.at_tracking_detection.detection.PermanentBluetoothScanner
import de.seemoo.at_tracking_detection.detection.ScanBluetoothWorker
import de.seemoo.at_tracking_detection.util.SharedPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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
            @OptIn(DelicateCoroutinesApi::class)
            goAsync {
                Timber.d("Running scan launched from Alert")
                BackgroundBluetoothScanner.scanInBackground(startedFrom = "ScheduleWorkersReceiver")
            }
        }

        // Initiate the permanent background scanner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedPrefs.usePermanentBluetoothScanner) {
            goAsync {
                Timber.d("Attempting to start PermanentBluetoothScanner from ScheduleWorkersReceiver")
                PermanentBluetoothScanner.scan()
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

// From: https://stackoverflow.com/questions/74111692/run-coroutine-functions-on-broadcast-receiver
fun BroadcastReceiver.goAsync(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
) {
    val pendingResult = goAsync()
    @OptIn(DelicateCoroutinesApi::class) // Must run globally; there's no teardown callback.
    GlobalScope.launch(context) {
        try {
            block()
        } catch (e: Exception) {
            Timber.e(e, "Error in async block for BroadcastReceiver")
        } finally {
            pendingResult.finish()
        }
    }
}