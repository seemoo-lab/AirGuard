package de.seemoo.at_tracking_detection.detection

import android.content.Context
import android.content.SharedPreferences
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
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.notifications.NotificationService
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@HiltWorker
class TrackingDetectorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationService: NotificationService,
    private val deviceRepository: DeviceRepository,
    private val beaconRepository: BeaconRepository,
    private val sharedPreferences: SharedPreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("Tracking detection background job started!")
        val ignoredDevices = deviceRepository.ignoredDevicesSync
        // remove devices which are ignored
        val cleanedBeaconsPerDevice = getLatestBeaconsPerDevice().filterKeys { address ->
            !ignoredDevices.map { it.address }.contains(address)
        }

        var notificaitonsSent = 0

        //TODO: Can we do this in parallel?
        cleanedBeaconsPerDevice.forEach { mapEntry ->
            //TODO: Implement tracking detection for Tile
            val device = deviceRepository.getDevice(mapEntry.key)

            //Check that we found enough beacons
            if (mapEntry.value.size < MAX_BEACONS_BEFORE_ALARM) {
                return@forEach
            }

            //Checks if the time difference between received beacons is large enough
            if (!isTrackingForEnoughTime(device, mapEntry.value)) {
                return@forEach
            }

            //Check that we do not send notifications too often (one notification every 8 hours)
            if (device?.lastNotificationSent != null) {
                val hoursPassed =
                    device.lastNotificationSent!!.until(LocalDateTime.now(), ChronoUnit.HOURS)
                if (hoursPassed < 8) {
                    return@forEach
                }
            }

            //Check if the beacon have travelled far enough
            if (useLocation && !hasMinBeaconDistance(mapEntry.value)) {
                return@forEach
            }

            Timber.d("Found more than $MAX_BEACONS_BEFORE_ALARM beacons per device... Sending Notification!")
            notificationService.sendTrackingNotification(mapEntry.key)
            device?.notificationSent = true
            device?.lastNotificationSent = LocalDateTime.now()
            device?.let { d -> deviceRepository.update(d) }
            notificaitonsSent += 1
        }

        Timber.d("Tracking detector worker finished. Sent $notificaitonsSent notifications")
        return Result.success(
            Data.Builder()
                .putInt("sentNotifications", notificaitonsSent)
                .build()
        )
    }

    private val useLocation = sharedPreferences.getBoolean("use_location", false)

    private fun getLatestBeaconsPerDevice(): HashMap<String, List<Beacon>> {
        val beaconsPerDevice: HashMap<String, List<Beacon>> = HashMap()
        val since = LocalDateTime.parse(
            sharedPreferences.getString(
                "last_scan",
                LocalDateTime.now(ZoneOffset.UTC).toString()
            )
        )
        //Gets all beacons found in the last scan. Then we get all beacons for the device that emitted one of those
        beaconRepository.getLatestBeacons(since).forEach {
            val beacons = beaconRepository.getDeviceBeacons(it.deviceAddress)
            beaconsPerDevice[it.deviceAddress] = beacons
        }
        return beaconsPerDevice
    }

    companion object {
        const val MAX_BEACONS_BEFORE_ALARM = 3

        /// Minimum tracking time
        const val MIN_TRACKING_TIME_SECONDS = 30 * 60
        const val MIN_DISTANCE_BETWEEN_BEACONS = 400

        //Moving some functions to the companion object to make them testable

        /**
         * Gets a list of beacons and checks if the user is beeing tracked for the minimum amount of
         * time before a notification is sent.
         * @param beacons
         * @param baseDevice
         * @return
         */
        fun isTrackingForEnoughTime(baseDevice: BaseDevice?, beacons: List<Beacon>): Boolean {
            if (beacons.isEmpty()) {
                return false
            }

            //Sort the list by received at
            //Last beacon received is last at the list
            val beaconsSorted = beacons.sortedBy { it.receivedAt }

            val firstBeacon = beaconsSorted.first()
            val lastBeacon = beaconsSorted.last()
            val timeDiff = ChronoUnit.SECONDS.between(firstBeacon.receivedAt, lastBeacon.receivedAt)
            val minTrackingTime =
                baseDevice?.device?.deviceContext?.minTrackingTime ?: MIN_TRACKING_TIME_SECONDS

            return timeDiff >= minTrackingTime
        }

        private fun hasMinBeaconDistance(beacons: List<Beacon>): Boolean {

            var distanceReached = false

            // Check first if any beacons meet the minimal distance requirement
            beacons.forEach { first ->
                beacons.forEach { second ->
                    if (
                        first.latitude != null && first.longitude != null &&
                        second.latitude != null && second.longitude != null
                    ) {
                        val firstLocation = getLocation(first.latitude!!, first.longitude!!)
                        val secondLocation = getLocation(second.latitude!!, second.longitude!!)

                        // Return true if any beacon pair full fills the minimal distance requirement
                        if (firstLocation.distanceTo(secondLocation) >= MIN_DISTANCE_BETWEEN_BEACONS) {
                            distanceReached = true
                        }
                    }
                }
            }

            return distanceReached
        }


        private fun getLocation(latitude: Double, longitude: Double): Location {
            val location = Location(LocationManager.GPS_PROVIDER)
            location.latitude = latitude
            location.longitude = longitude
            return location
        }

    }

}