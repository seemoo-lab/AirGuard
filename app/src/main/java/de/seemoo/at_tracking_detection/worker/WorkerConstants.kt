package de.seemoo.at_tracking_detection.worker

object WorkerConstants {
    const val MIN_MINUTES_TO_NEXT_BT_SCAN = 15L
    const val MIN_HOURS_TO_NEXT_SEND_STATISTICS = 4L
    const val PERIODIC_SCAN_WORKER = "PeriodicScanWorker"
    const val PERIODIC_SEND_STATISTICS_WORKER = "PeriodicSendStatisticsWorker"
    const val TRACKING_DETECTION_WORKER = "TrackingDetectionWorker"
    const val IGNORE_DEVICE_WORKER = "IgnoreDeviceWorker"
    const val FALSE_ALARM_WORKER = "FalseAlarmWorker"
    const val KIND_DELAY = 1L
}