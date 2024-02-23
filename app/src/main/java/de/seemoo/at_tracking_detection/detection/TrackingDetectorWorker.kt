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
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

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
        deleteOldAndSafeTrackers()

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
            val device = deviceRepository.getDevice(mapEntry.key) ?: return@forEach
            val useLocation = SharedPrefs.useLocationInTrackingDetection

            if (throwNotification(device, useLocation)) {
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

    /**
     * Retrieves the devices detected during the last scan (last 15min)
     * @return a HashMap with the device address as key and the list of beacons as value (all beacons in the relevant interval)
     */
    private fun getLatestBeaconsPerDevice(): ConcurrentHashMap<String, List<Beacon>> {
        val beaconsPerDevice: ConcurrentHashMap<String, List<Beacon>> = ConcurrentHashMap()
        val since = SharedPrefs.lastScanDate?.minusMinutes(15) ?: LocalDateTime.now().minusMinutes(30)
        //Gets all beacons found in the last scan. Then we get all beacons for the device that emitted one of those
        beaconRepository.getLatestBeacons(since).forEach {
            // Only retrieve the last two weeks since they are only relevant for tracking
            val beacons = beaconRepository.getDeviceBeaconsSince(it.deviceAddress, RiskLevelEvaluator.relevantTrackingDateForRiskCalculation)
            beaconsPerDevice[it.deviceAddress] = beacons
        }
        return beaconsPerDevice
    }

    private fun throwNotification(device: BaseDevice, useLocation: Boolean): Boolean {
        val minNumberOfLocations: Int = RiskLevelEvaluator.getNumberOfLocationsToBeConsideredForTrackingDetection(device.deviceType)
        val minTrackedTime: Long = RiskLevelEvaluator.getMinutesAtLeastTrackedBeforeAlarm() // in minutes

        val deviceIdentifier: String = device.address
        val relevantHours: Long = device.deviceType?.getNumberOfHoursToBeConsideredForTrackingDetection() ?: RiskLevelEvaluator.RELEVANT_HOURS_TRACKING
        var considerDetectionEventSince: LocalDateTime = RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection(relevantHours)

        val lastNotificationSent = device.lastNotificationSent
        if (lastNotificationSent != null && lastNotificationSent > considerDetectionEventSince && lastNotificationSent < LocalDateTime.now()) {
            considerDetectionEventSince = lastNotificationSent
        }

        val detectionEvents: List<Beacon> = beaconRepository.getDeviceBeaconsSince(deviceIdentifier, considerDetectionEventSince)

        val detectionEventsSorted: List<Beacon> = detectionEvents.sortedBy { it.receivedAt }
        val earliestDetectionEvent: Beacon = detectionEventsSorted.firstOrNull() ?: return false
        val timeFollowing: Long = Duration.between(earliestDetectionEvent.receivedAt, LocalDateTime.now()).toMinutes()

        val filteredDetectionEvents = detectionEvents.filter { it.locationId != null && it.locationId != 0 }
        val distinctDetectionEvent = filteredDetectionEvents.map { it.locationId }.distinct()
        val locations = distinctDetectionEvent.size

        if (timeFollowing >= minTrackedTime) {
            if (locations >= minNumberOfLocations || !useLocation) {
                return true
            }
        }
        return false
    }

    private suspend fun deleteOldAndSafeTrackers() {
        // Delete old devices and beacons from the database
        Timber.d("Deleting old and safe Trackers")
        val deleteSafeTrackersBefore = RiskLevelEvaluator.deleteBeforeDate
        val beaconsToBeDeleted = beaconRepository.getBeaconsOlderThanWithoutNotifications(deleteSafeTrackersBefore)
        beaconRepository.deleteBeacons(beaconsToBeDeleted)
        val devicesToBeDeleted = deviceRepository.getDevicesOlderThanWithoutNotifications(deleteSafeTrackersBefore)
        deviceRepository.deleteDevices(devicesToBeDeleted)
        Timber.d("Deleting successful")
    }

    companion object {
        fun getLocation(latitude: Double, longitude: Double): Location {
            val location = Location(LocationManager.GPS_PROVIDER)
            location.latitude = latitude
            location.longitude = longitude
            return location
        }
    }

}