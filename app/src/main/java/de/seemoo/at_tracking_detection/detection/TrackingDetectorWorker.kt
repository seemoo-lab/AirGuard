package de.seemoo.at_tracking_detection.detection

import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.risk.RiskLevel
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@HiltWorker
class TrackingDetectorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationService: NotificationService,
    private val deviceRepository: DeviceRepository,
    private val beaconRepository: BeaconRepository,
    private val notificationRepository: NotificationRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("Tracking detection background job started!")
        // Just writing a new comment in here.
        val ignoredDevices = deviceRepository.ignoredDevicesSync

        // All beacons in the last 14 days for devices detected during the last scan
        val latestBeaconsPerDevice = getLatestBeaconsPerDevice()
        // remove devices which are ignored
        val cleanedBeaconsPerDevice = latestBeaconsPerDevice.filterKeys { address ->
            !ignoredDevices.map { it.address }.contains(address)
        }

        var notificationsSent = 0

        cleanedBeaconsPerDevice.forEach { mapEntry ->
            val device = deviceRepository.getDevice(mapEntry.key)

            if (device != null && RiskLevelEvaluator.checkRiskLevelForDevice(device, useLocation) != RiskLevel.LOW && checkLastNotification(device)) {
                // Send Notification
                Timber.d("Conditions for device ${device.address} being a tracking device are true... Sending Notification!")
                notificationService.sendTrackingNotification(device)
                device.notificationSent = true
                device.lastNotificationSent = LocalDateTime.now()
                device.let { d -> deviceRepository.update(d) }
                notificationsSent += 1
            } else {
                return@forEach
            }
        }

        Timber.d("Tracking detector worker finished. Sent $notificationsSent notifications")
        return Result.success(
            Data.Builder()
                .putInt("sentNotifications", notificationsSent)
                .build()
        )
    }

    private val useLocation = SharedPrefs.useLocationInTrackingDetection

    /**
     * Retrieves the devices detected during the last scan (last 15min)
     * @return a HashMap with the device address as key and the list of beacons as value (all beacons in the relevant interval)
     */
    private fun getLatestBeaconsPerDevice(): HashMap<String, List<Beacon>> {
        val beaconsPerDevice: HashMap<String, List<Beacon>> = HashMap()
        val since = SharedPrefs.lastScanDate?.minusMinutes(15) ?: LocalDateTime.now().minusMinutes(30)
        //Gets all beacons found in the last scan. Then we get all beacons for the device that emitted one of those
        beaconRepository.getLatestBeacons(since).forEach {
            // Only retrieve the last two weeks since they are only relevant for tracking
            val beacons = beaconRepository.getDeviceBeaconsSince(it.deviceAddress, RiskLevelEvaluator.relevantTrackingDate)
            beaconsPerDevice[it.deviceAddress] = beacons
        }
        return beaconsPerDevice
    }

    companion object {
        fun getLocation(latitude: Double, longitude: Double): Location {
            val location = Location(LocationManager.GPS_PROVIDER)
            location.latitude = latitude
            location.longitude = longitude
            return location
        }

        private fun checkLastNotification(device: BaseDevice): Boolean {
            return if (device.lastNotificationSent != null) {
                val hoursPassed = device.lastNotificationSent!!.until(LocalDateTime.now(), ChronoUnit.HOURS)
                // Last Notification longer than 8 hours
                hoursPassed >= RiskLevelEvaluator.HOURS_AT_LEAST_UNTIL_NEXT_NOTIFICATION
            } else{
                true
            }
        }
    }

}