package de.seemoo.at_tracking_detection.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.detection.PermanentBluetoothScanner
import de.seemoo.at_tracking_detection.detection.ScanBluetoothWorker
import de.seemoo.at_tracking_detection.util.SharedPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class ScheduleWorkersReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("Broadcast received ${intent?.action}")

        val action = intent?.action
        val backgroundWorkScheduler = ATTrackingDetectionApplication.getCurrentApp().backgroundWorkScheduler

        // Keep the broadcast short: schedule alarms inline; offload WorkManager to a separate thread and finish quickly.
        goAsync(Dispatchers.Default) {
            when (action) {
                // App woke up to make sure background schedule exists. Keep it light.
                "AlarmManagerWakeUp_Schedule_BackgroundScan" -> {
                    // Offload WorkManager work
                    Thread {
                        try {
                            backgroundWorkScheduler.launch()
                            if (SharedPrefs.shareData) {
                                backgroundWorkScheduler.scheduleShareData()
                            }
                        } catch (t: Throwable) {
                            Timber.w(t, "Failed scheduling periodic work from receiver")
                        } finally {
                            BackgroundWorkScheduler.scheduleAlarmWakeupIfScansFail()
                        }
                    }.start()
                }

                // Our exact/alarm fired to perform a scan: enqueue work, don't run the scan inline.
                "AlarmManagerWakeUp_Perform_BackgroundScan" -> {
                    // Reschedule next alarm now (cheap)
                    BackgroundWorkScheduler.scheduleScanWithAlarm()
                    // Offload WorkManager enqueue to separate thread so we return fast
                    Thread {
                        try {
                            backgroundWorkScheduler.scheduleImmediateBackgroundScan()
                        } catch (t: Throwable) {
                            Timber.w(t, "Failed to enqueue immediate scan from receiver")
                        }
                    }.start()
                }

                // System broadcasts: keep receiver fast; just (re)establish lightweight schedules.
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    // Only (re)schedule alarms on boot/package replace. Avoid WorkManager initialization here.
                    BackgroundWorkScheduler.scheduleScanWithAlarm()
                    BackgroundWorkScheduler.scheduleAlarmWakeupIfScansFail()
                }

                else -> Timber.w("Unhandled broadcast action: $action")
            }
        }

        // Start the permanent scanner in a detached way
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedPrefs.usePermanentBluetoothScanner) {
            goAsync(Dispatchers.Default) {
                try {
                    Timber.d("Attempting to start PermanentBluetoothScanner from ScheduleWorkersReceiver")
                    PermanentBluetoothScanner.scan()
                } catch (t: Throwable) {
                    Timber.w(t, "Failed starting PermanentBluetoothScanner from receiver")
                }
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