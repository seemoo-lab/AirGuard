package de.seemoo.at_tracking_detection.statistics

import android.content.Context
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
        val lastDataDonation = SharedPrefs.lastDataDonation ?: LocalDateTime.MIN

        try {
            if (!api.ping().isSuccessful) {
                Timber.e("Server not available!")
                return Result.retry()
            }
        }catch (e: Error) {
            Timber.e("Failed pinging server")
            Timber.e(e)
            return Result.retry()
        }


        if (token == null) {
            try {
                val response = api.getToken().body() ?: return Result.retry()
                token = response.token
                SharedPrefs.token = token
            }catch (e: Error) {
                Timber.e("Failed getting token")
                Timber.e(e)
                return Result.retry()
            }catch (e: Exception) {
                Timber.e("Failed getting token")
                Timber.e(e.stackTrace.toString())
                return Result.retry()
            }
        }


        val oneWeekAgo = LocalDateTime.now().minusWeeks(1)
        var uploadDateTime: LocalDateTime = if (lastDataDonation > oneWeekAgo) {
                lastDataDonation
            }else {
                oneWeekAgo
            }

        if (BuildConfig.DEBUG) {
            uploadDateTime = LocalDateTime.now().minusDays(1)
        }

        // Get all devices last seen, since the last upload time
        val devices = deviceRepository.getDeviceBeaconsSinceDate(uploadDateTime)

        if (devices.isEmpty()) {
            Timber.d("Nothing to send...")
            SharedPrefs.lastDataDonation = LocalDateTime.now()
            return Result.success()
        }

        // Remove sensitive data
        devices.forEach {
            it.address = ""
            // Remove beacons that have already been uploaded
            it.beacons = it.beacons.filter { beacon ->
                beacon.receivedAt >= uploadDateTime
            }
            it.beacons.forEach { beacon ->
                // Remove location and device address
                beacon.locationId = null
                beacon.deviceAddress = ""
            }
            // Remove notifications that have already been uploaded
            it.notifications = it.notifications.filter { notification ->
                notification.createdAt >= uploadDateTime
            }
        }

        if (!BuildConfig.DEBUG) {
            // This makes sure that no devices will be sent twice. If the donation fails, then the app
            // will upload newer data the next time.
            SharedPrefs.lastDataDonation = LocalDateTime.now()
        }
        Timber.d("Trying to upload ${devices.size} devices")
        try {
            if (!api.donateData(token = token, devices=devices).isSuccessful) {
                return Result.retry()
            }
        }catch (e: Error) {
            Timber.e("Failed uploading devices")
            Timber.e(e)
            return Result.retry()
        }

        Timber.d("${devices.size} devices shared!")
        SharedPrefs.lastDataDonation = LocalDateTime.now()
        return Result.success()
    }
}