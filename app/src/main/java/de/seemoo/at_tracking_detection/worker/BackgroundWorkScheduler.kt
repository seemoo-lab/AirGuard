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
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val backgroundWorkBuilder: BackgroundWorkBuilder
) {

    fun launch() {
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
    }
}