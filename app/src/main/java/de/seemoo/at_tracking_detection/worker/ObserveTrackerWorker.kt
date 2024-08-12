package de.seemoo.at_tracking_detection.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import timber.log.Timber

class ObserveTrackerWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    // TODO: Test if this still works
    override suspend fun doWork(): Result {
        Timber.d("ObserveTrackerWorker doWork() called")
        val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
        val notificationService = ATTrackingDetectionApplication.getCurrentApp().notificationService

        val inputData = inputData
        val deviceAddress = inputData.getString(DEVICE_ADDRESS_PARAM)
        if (deviceAddress != null) {
            Timber.d("Received deviceAddress: $deviceAddress")
            val device = deviceRepository.getDevice(deviceAddress)
            if (device != null) {
                val currentObservationDuration = device.currentObservationDuration
                val nextObservationNotification = device.nextObservationNotification
                val lastSeen = device.lastSeen

                if (nextObservationNotification != null && currentObservationDuration != null) {
                    val observationPositive = (lastSeen >= nextObservationNotification.minusMinutes(ScheduleWorkersReceiver.OBSERVATION_DELTA) && lastSeen <= nextObservationNotification.plusMinutes(currentObservationDuration))

                    Timber.d("Observation for device ${device.address} is over... Sending Notification!")
                    device.nextObservationNotification = null
                    device.currentObservationDuration = null

                    val deviceTypeString: String = device.deviceType?.let {
                        DeviceManager.deviceTypeToString(
                            it
                        )
                    }
                        ?: "UNKNOWN"

                    notificationService.sendObserveTrackerNotification(device.address, deviceTypeString, currentObservationDuration, observationPositive)

                    // Update device
                    deviceRepository.update(device)
                }
            } else {
                notificationService.sendObserveTrackerFailedNotification()
            }
        } else {
            notificationService.sendObserveTrackerFailedNotification()
        }
        return Result.success()
    }

    companion object {
        // Keys to access parameters
        const val DEVICE_ADDRESS_PARAM = "DEVICE_ADDRESS"
    }
}