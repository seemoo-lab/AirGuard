package de.seemoo.at_tracking_detection.worker

import androidx.work.*
import de.seemoo.at_tracking_detection.detection.ScanBluetoothWorker
import de.seemoo.at_tracking_detection.detection.TrackingDetectorWorker
import de.seemoo.at_tracking_detection.notifications.worker.FalseAlarmWorker
import de.seemoo.at_tracking_detection.notifications.worker.IgnoreDeviceWorker
import de.seemoo.at_tracking_detection.statistics.SendStatisticsWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundWorkBuilder @Inject constructor() {

    fun buildScanWorker(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<ScanBluetoothWorker>(
        WorkerConstants.MIN_MINUTES_TO_NEXT_BT_SCAN,
        TimeUnit.MINUTES
    ).addTag(WorkerConstants.PERIODIC_SCAN_WORKER)
        .setBackoffCriteria(BackoffPolicy.LINEAR, WorkerConstants.KIND_DELAY, TimeUnit.SECONDS)
        .build()

    fun buildImmediateScanWorker(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ScanBluetoothWorker>().addTag(WorkerConstants.SCAN_IMMEDIATELY)
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkerConstants.KIND_DELAY, TimeUnit.MINUTES)
            .build()

    fun buildSendStatisticsWorker(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<SendStatisticsWorker>(
            WorkerConstants.MIN_HOURS_TO_NEXT_SEND_STATISTICS, TimeUnit.HOURS
        ).addTag(WorkerConstants.PERIODIC_SEND_STATISTICS_WORKER)
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkerConstants.KIND_DELAY, TimeUnit.HOURS)
            .setConstraints(buildConstraints())
            .build()

    /*Send statistics now*/
    fun buildSendStatisticsWorkerDebug(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<SendStatisticsWorker>().addTag(WorkerConstants.ONETIME_SEND_STATISTICS_WORKER)
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkerConstants.KIND_DELAY, TimeUnit.HOURS)
            .setConstraints(buildConstraints())
            .build()

    fun buildTrackingDetectorWorker(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<TrackingDetectorWorker>().addTag(WorkerConstants.TRACKING_DETECTION_WORKER)
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkerConstants.KIND_DELAY, TimeUnit.MINUTES)
            .build()

    fun buildIgnoreDeviceWorker(deviceAddress: String, notificationId: Int): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<IgnoreDeviceWorker>().addTag(WorkerConstants.IGNORE_DEVICE_WORKER)
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkerConstants.KIND_DELAY, TimeUnit.MINUTES)
            .setInputData(
                Data.Builder().putString("deviceAddress", deviceAddress)
                    .putInt("notificationId", notificationId).build()
            )
            .build()

    fun buildFalseAlarmWorker(notificationId: Int): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<FalseAlarmWorker>().addTag(WorkerConstants.FALSE_ALARM_WORKER)
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkerConstants.KIND_DELAY, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putInt("notificationId", notificationId).build())
            .build()

    private fun buildConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

}