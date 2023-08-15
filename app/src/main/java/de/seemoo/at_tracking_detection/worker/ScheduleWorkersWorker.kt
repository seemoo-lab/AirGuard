package de.seemoo.at_tracking_detection.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import timber.log.Timber

class ScheduleWorkersWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("ScheduleWorkersWorker doWork() called")
        // Perform the work you need to do after the delay here
        val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository!!
        val notificationService = ATTrackingDetectionApplication.getCurrentApp()?.notificationService!!

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
                    // TODO find a better solution, so call Bluetooth Check here
                    val observationPositive = (lastSeen >= nextObservationNotification.minusMinutes(ScheduleWorkersReceiver.OBSERVATION_DELTA) && lastSeen <= nextObservationNotification.plusMinutes(currentObservationDuration))

                    Timber.d("Observation for device ${device.address} is over... Sending Notification!")
                    device.nextObservationNotification = null
                    device.currentObservationDuration = null
                    notificationService.sendObserveTrackerNotification(device.address, currentObservationDuration, observationPositive)

                    // Update device
                    deviceRepository.update(device)
                }
            }
        }
        return Result.success()
    }

    companion object {
        // Keys to access parameters
        const val DEVICE_ADDRESS_PARAM = "DEVICE_ADDRESS"
    }
}