package de.seemoo.at_tracking_detection.worker

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.work.*
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.util.Utility
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import android.Manifest
import de.seemoo.at_tracking_detection.util.SharedPrefs

@Singleton
class BackgroundWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val backgroundWorkBuilder: BackgroundWorkBuilder
) {

    fun launch() {
        // We now scan with Alarms instead of periodic workers, since they have proven to be unreliable
        scheduleScanWithAlarm()
        // We cancel all periodic scans
        workManager.cancelUniqueWork(WorkerConstants.PERIODIC_SCAN_WORKER)
    }

    fun legacyLaunch() {
        Timber.d("Work scheduler started!")
        workManager.enqueueUniquePeriodicWork(
            WorkerConstants.PERIODIC_SCAN_WORKER,
            ExistingPeriodicWorkPolicy.KEEP,
            backgroundWorkBuilder.buildScanWorker()
        ).also { operation ->
            operation.logOperationSchedule(WorkerConstants.PERIODIC_SCAN_WORKER)
            operation.result.addListener({ scheduleTrackingDetector() }, { it.run() })
        }
    }

    fun scheduleImmediateBackgroundScan() {
        Timber.d("Scheduling Immediate Background Scan Worker ")
        workManager.enqueueUniqueWork(WorkerConstants.SCAN_IMMEDIATELY,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            backgroundWorkBuilder.buildImmediateScanWorker())
    }


    fun getState(uniqueWorkName: String): LiveData<WorkInfo.State?> =
        workManager.getWorkInfosByTagLiveData(uniqueWorkName).map { it.lastOrNull()?.state }

    fun scheduleTrackingDetector() = workManager.enqueueUniqueWork(
        WorkerConstants.TRACKING_DETECTION_WORKER,
        ExistingWorkPolicy.REPLACE,
        backgroundWorkBuilder.buildTrackingDetectorWorker()
    ).also { it.logOperationSchedule(WorkerConstants.TRACKING_DETECTION_WORKER) }

    fun scheduleShareData() = workManager.enqueueUniquePeriodicWork(
        WorkerConstants.PERIODIC_SEND_STATISTICS_WORKER,
        ExistingPeriodicWorkPolicy.KEEP,
        backgroundWorkBuilder.buildSendStatisticsWorker()
    ).also { it.logOperationSchedule(WorkerConstants.PERIODIC_SEND_STATISTICS_WORKER) }

    fun scheduleShareDataDebug() = workManager.enqueueUniqueWork(
        WorkerConstants.ONETIME_SEND_STATISTICS_WORKER,
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        backgroundWorkBuilder.buildSendStatisticsWorkerDebug()
    ).also { it.logOperationSchedule(WorkerConstants.ONETIME_SEND_STATISTICS_WORKER) }

    fun removeShareData() =
        workManager.cancelUniqueWork(WorkerConstants.PERIODIC_SEND_STATISTICS_WORKER)

    fun scheduleIgnoreDevice(deviceAddress: String, notificationId: Int) =
        workManager.enqueueUniqueWork(
            WorkerConstants.IGNORE_DEVICE_WORKER,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            backgroundWorkBuilder.buildIgnoreDeviceWorker(deviceAddress, notificationId)
        ).also { it.logOperationSchedule(WorkerConstants.IGNORE_DEVICE_WORKER) }

    fun scheduleFalseAlarm(notificationId: Int) = workManager.enqueueUniqueWork(
        WorkerConstants.IGNORE_DEVICE_WORKER,
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        backgroundWorkBuilder.buildFalseAlarmWorker(notificationId)
    ).also { it.logOperationSchedule(WorkerConstants.FALSE_ALARM_WORKER) }

    private fun Operation.logOperationSchedule(uniqueWorker: String) =
        this.result.addListener({ Timber.d("$uniqueWorker completed!") }, { it.run() })
            .also { Timber.d("$uniqueWorker scheduled!") }

    companion object {
        /**
         * If the app does not scan in background for two hours the AlarmManager will execute and call the `ScheduleWorkersReceiver` to initiate a new background scan
         */
        @SuppressLint("UnspecifiedImmutableFlag")
        fun scheduleAlarmWakeupIfScansFail() {
            //Run in 2 hours
//            val timeInMillisUntilNotification: Long = 2 * 60 * 60 * 1000
            // Run in 60minBack
            val timeInMillisUntilNotification: Long = 60 * 60 * 1000

            val alarmDate = LocalDateTime.now().plus(timeInMillisUntilNotification, ChronoUnit.MILLIS)
            val alarmTime = System.currentTimeMillis() + timeInMillisUntilNotification

            val intent = Intent(ATTrackingDetectionApplication.getAppContext(), ScheduleWorkersReceiver::class.java)
            intent.action = "AlarmManagerWakeUp_Schedule_BackgroundScan"

            val pendingIntent = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.getBroadcast(ATTrackingDetectionApplication.getAppContext(), -103,intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            }else {
                PendingIntent.getBroadcast(ATTrackingDetectionApplication.getAppContext(), -103, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            val alarmManager = ATTrackingDetectionApplication.getAppContext().getSystemService(
                Context.ALARM_SERVICE) as AlarmManager

            alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
            Timber.d("Scheduled an alarm to reschedule the scan at $alarmDate")
        }

        fun scheduleScanWithAlarm() {
            //Run in 15 min
            val timeInMillisUntilNotification: Long = if (BuildConfig.DEBUG) {
                15 * 60 * 1000
            } else {
                15 * 60 * 1000
            }

            val alarmDate = LocalDateTime.now().plus(timeInMillisUntilNotification, ChronoUnit.MILLIS)
            val alarmTime = System.currentTimeMillis() + timeInMillisUntilNotification

            val intent = Intent(ATTrackingDetectionApplication.getAppContext(), ScheduleWorkersReceiver::class.java)
            intent.action = "AlarmManagerWakeUp_Perform_BackgroundScan"

            val pendingIntent = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.getBroadcast(ATTrackingDetectionApplication.getAppContext(), -103,intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            }else {
                PendingIntent.getBroadcast(ATTrackingDetectionApplication.getAppContext(), -103, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            val alarmManager = ATTrackingDetectionApplication.getAppContext().getSystemService(
                Context.ALARM_SERVICE) as AlarmManager

            // We use exact Alarms since we want regular background scans to happen.
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
                Timber.d("Scheduled an setExactAndAllowWhileIdle alarm to start a scan at $alarmDate")
            }catch (exception: SecurityException) {
                try {
                    Timber.w("Failed scheduling setExactAndAllowWhileIdle Alarm scan")
                    // Alarm could not be scheduled because user disallowed battery exception
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
                    Timber.d("Scheduled an setExact alarm to start a scan at $alarmDate")
                }catch (exception: SecurityException) {
                    Timber.w("Failed scheduling setExact Alarm scan")
                    // Alarm could not be scheduled because user disallowed battery exception
                    alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
                    Timber.d("Scheduled an set alarm to start a scan at $alarmDate")
                }
            }

            SharedPrefs.nextScanDate = alarmDate
        }
    }
}