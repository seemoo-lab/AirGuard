package de.seemoo.at_tracking_detection.detection

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler

@HiltWorker
class ScanBluetoothWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    var backgroundWorkScheduler: BackgroundWorkScheduler,
) :
    CoroutineWorker(appContext, workerParams) {


    override suspend fun doWork(): Result {
        val ignoreDeactivatedSetting = inputData.getBoolean(IGNORE_DEACTIVATED_SETTING_PARAM, false)

        val results = BackgroundBluetoothScanner.scanInBackground(
            startedFrom = "ScanBluetoothWorker",
            ignoreDeactivatedSetting = ignoreDeactivatedSetting
        )

        if (results.failed) {
            return Result.retry()
        }

        return Result.success(
            Data.Builder()
                .putLong("duration", results.duration)
                .putInt("mode", results.scanMode)
                .putInt("devicesFound", results.numberDevicesFound)
                .build()
        )
    }

    companion object {
        const val IGNORE_DEACTIVATED_SETTING_PARAM = "IGNORE_DEACTIVATED_SETTING"
    }
}
