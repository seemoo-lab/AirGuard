package de.seemoo.at_tracking_detection.statistics

import android.content.Context
import android.content.SharedPreferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.statistics.api.Api
import de.seemoo.at_tracking_detection.util.SharedPrefs
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@HiltWorker
class SendStatisticsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val api: Api,
    private val deviceRepository: DeviceRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (BuildConfig.DEBUG) {
            Timber.d("Not sending any data. Debug mode")
            return Result.success()
        }
        var token = SharedPrefs.token
        val lastDataDonation = SharedPrefs.lastDataDonation

        if (!api.ping().isSuccessful) {
            Timber.e("Server not available!")
            return Result.retry()
        }

        if (token == null) {
            val response = api.getToken().body() ?: return Result.retry()
            token = response.token
            SharedPrefs.token = token
        }

        val devices = deviceRepository.getDeviceBeaconsSinceDate(lastDataDonation)

        if (devices.isEmpty()) {
            Timber.d("Nothing to send...")
            return Result.success()
        }

        // Remove sensitive data
        devices.forEach {
            it.address = ""
            it.beacons.forEach { beacon ->
                beacon.latitude = null
                beacon.longitude = null
                beacon.deviceAddress = ""
            }
        }

        if (!api.donateData(token, devices).isSuccessful) {
            return Result.retry()
        }

        Timber.d("${devices.size} devices shared!")

        return Result.success()
    }
}