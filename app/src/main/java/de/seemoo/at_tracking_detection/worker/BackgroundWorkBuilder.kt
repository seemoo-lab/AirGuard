package de.seemoo.at_tracking_detection.worker

import androidx.work.*
import de.seemoo.at_tracking_detection.detection.TrackingDetectorWorker
import de.seemoo.at_tracking_detection.statistics.SendStatisticsWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundWorkBuilder @Inject constructor() {

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

    private fun buildConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

}