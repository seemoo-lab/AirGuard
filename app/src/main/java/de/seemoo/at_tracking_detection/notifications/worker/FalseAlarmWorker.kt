package de.seemoo.at_tracking_detection.notifications.worker

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.seemoo.at_tracking_detection.database.viewmodel.NotificationViewModel
import timber.log.Timber

@HiltWorker
class FalseAlarmWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationViewModel: NotificationViewModel,
    private val notificationManager: NotificationManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val notificationId = inputData.getInt("notificationId", -1)
        if (notificationId == -1) {
            Timber.e("No notification id passed!")
            return Result.failure()
        }
        notificationViewModel.setFalseAlarm(notificationId, true)
        //TODO: cancel specific notification by calling cancel(notificationId) which somehow doesn't work...
        notificationManager.cancelAll()
        Timber.d("Marked notification $notificationId as false alarm!")
        return Result.success()
    }
}