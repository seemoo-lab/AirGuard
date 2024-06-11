package de.seemoo.at_tracking_detection.detection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.database.models.device.*
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getPublicKey
import de.seemoo.at_tracking_detection.database.models.Location as LocationModel
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.ble.BLEScanCallback
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import de.seemoo.at_tracking_detection.detection.TrackingDetectorWorker.Companion.getLocation
import kotlinx.coroutines.delay
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@HiltWorker
class ScanBluetoothWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    var backgroundWorkScheduler: BackgroundWorkScheduler,
) :
    CoroutineWorker(appContext, workerParams) {


    override suspend fun doWork(): Result {

        val results =  BackgroundBluetoothScanner.scanInBackground(startedFrom = "ScanBluetoothWorker")

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
}
