package de.seemoo.at_tracking_detection.detection

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.tables.Beacon
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

        cleanedBeaconsPerDevice.forEach { mapEntry ->
            //Check that we found enough beacons
            if (mapEntry.value.size < MAX_BEACONS_BEFORE_ALARM) {
                return@forEach
            }

            //Check that we do not send notifications too often (one notification every 8 hours)
            val device = deviceRepository.getDevice(mapEntry.key)
            if (device?.lastNotificationSent != null) {
                val hoursPassed =
                    device.lastNotificationSent!!.until(LocalDateTime.now(), ChronoUnit.HOURS)
                if (hoursPassed < 8) {
                    return@forEach
                }
            }

            //Check if the beacon have travelled far enough
            if (!hasMinBeaconDistance(mapEntry.value)) {
                return@forEach
            }

            Timber.d("Found more than $MAX_BEACONS_BEFORE_ALARM beacons per device... Sending Notification!")
            notificationService.sendTrackingNotification(mapEntry.key)
            device?.notificationSent = true
            device?.lastNotificationSent = LocalDateTime.now()
            device?.let { d -> deviceRepository.update(d) }
        }

        return Result.success()
    }

    private val useLocation = sharedPreferences.getBoolean("use_location", true)

    private fun hasMinBeaconDistance(beacons: List<Beacon>): Boolean {

        // handle the case
        // where the user decides to not use the location after a while
        if (!useLocation) {
            return true
        }

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

    private fun getLatestBeaconsPerDevice(): HashMap<String, List<Beacon>> {
        val beaconsPerDevice: HashMap<String, List<Beacon>> = HashMap()
        val since = LocalDateTime.parse(
            sharedPreferences.getString(
                "last_scan",
                LocalDateTime.now(ZoneOffset.UTC).toString()
            )
        )
        beaconRepository.getLatestBeacons(since).forEach {
            val beacons = beaconRepository.getDeviceBeacons(it.deviceAddress)
            beaconsPerDevice[it.deviceAddress] = beacons
        }
        return beaconsPerDevice
    }

    companion object {
        const val MAX_BEACONS_BEFORE_ALARM = 3
        const val MIN_DISTANCE_BETWEEN_BEACONS = 400
    }
}