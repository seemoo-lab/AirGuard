package de.seemoo.at_tracking_detection.notifications.worker

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.seemoo.at_tracking_detection.database.viewmodel.DeviceViewModel
import timber.log.Timber

@HiltWorker
class IgnoreDeviceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val deviceViewModel: DeviceViewModel,
    private val notificationManagerCompat: NotificationManagerCompat,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val deviceAddress = inputData.getString("deviceAddress")
        if (deviceAddress == null) {
            Timber.e("No device Address passed!")
            return Result.failure()
        }
        val notificationId = inputData.getInt("notificationId", -1)
        if (notificationId == -1) {
            Timber.e("No notification id passed!")
            return Result.failure()
        }
        deviceViewModel.setIgnoreFlag(deviceAddress, true)
        //TODO: cancel specific notification by calling cancel(notificationId) which somehow doesn't work...
        notificationManagerCompat.cancelAll()
        Timber.d("Added device $deviceAddress to the ignored devices list!")
        return Result.success()
    }
}