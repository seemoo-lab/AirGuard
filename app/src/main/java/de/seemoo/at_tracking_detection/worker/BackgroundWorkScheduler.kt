package de.seemoo.at_tracking_detection.worker

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.work.*
import timber.log.Timber
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
}