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
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
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

        try {
            checkTooManyNotificationsHint()
        } catch (e: Exception) {
            Timber.e("Checking too many notifications hint failed: $e")
        }

        try {
            deleteSafeGoogleTrackers()
            deleteOldAndSafeTrackers()
        } catch (e:Exception) {
            Timber.e("Deleting trackers failed $e")
        }

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

    private suspend fun deleteSafeGoogleTrackers() {
        // This function is necessary because:
        // If an adversary constructs a device in the Find My Network where the connection state bit is always false, we still need to map this
        // We assume that after a fixed time (e.g. 12 hours) it is not the if there has been no indications that it is a malicious tracking device
        Timber.d("Start deleting safe google trackers")
        val deleteSafeTrackersBefore = RiskLevelEvaluator.deleteSafeGoogleTrackersBeforeDate

        try {
            val googleTrackersToBeDeleted = deviceRepository.getDevicesWithDeviceTypeAndConnectionStateOlderThan(
                DeviceType.GOOGLE_FIND_MY_NETWORK,
                ConnectionState.PREMATURE_OFFLINE,
                deleteSafeTrackersBefore
            )
            if (googleTrackersToBeDeleted.isNotEmpty()) {
                Timber.d("Deleting ${googleTrackersToBeDeleted.size} google trackers")
                deviceRepository.deleteDevices(googleTrackersToBeDeleted)
                Timber.d("Deleting Google Trackers successful")
            } else {
                Timber.d("No Google trackers to delete")
            }
        } catch (e: Exception) {
            Timber.e("Deleting Safe Google Trackers failed $e")
        }
    }

    private suspend fun deleteOldAndSafeTrackers() {
        // Delete old devices and beacons from the database
        Timber.d("Start deleting old and safe Trackers")

        val deleteTrackers = SharedPrefs.deleteOldDevices

        if (!deleteTrackers) {
            Timber.d("Deleting old devices is disabled in settings, skipping deletion")
            return
        }

        val alsoDeleteUnsafeTrackers = SharedPrefs.deleteUnsafeOldDevices
        val deleteSafeTrackersBefore = RiskLevelEvaluator.deleteBeforeDate

        try {
            val devicesToBeDeleted = if (alsoDeleteUnsafeTrackers) {
                Timber.d("Deleting all devices older than $deleteSafeTrackersBefore")
                deviceRepository.getDevicesOlderThan(deleteSafeTrackersBefore)
            } else {
                Timber.d("Only deleting safe trackers older than $deleteSafeTrackersBefore")
                deviceRepository.getDevicesOlderThanWithoutNotifications(deleteSafeTrackersBefore)
            }

            if (devicesToBeDeleted.isNotEmpty()) {
                Timber.d("Deleting ${devicesToBeDeleted.size} devices")
                deviceRepository.deleteDevices(devicesToBeDeleted)
                Timber.d("Deleting Devices successful")
            } else {
                Timber.d("No old devices to delete")
            }
        } catch (e: Exception) {
            Timber.e("Deleting Devices failed $e")
        }

        try {
            val locationRepository = ATTrackingDetectionApplication.getCurrentApp().locationRepository
            val locationsToBeDeleted = locationRepository.getLocationsWithNoBeacons()
            if (locationsToBeDeleted.isNotEmpty()) {
                Timber.d("Deleting ${locationsToBeDeleted.size} locations")
                locationRepository.deleteLocations(locationsToBeDeleted)
                Timber.d("Deleting Locations successful")
            } else {
                Timber.d("No locations to delete")
            }
        } catch (e: Exception) {
            Timber.e("Deleting Locations failed $e")
        }

        Timber.d("Deleting old and safe Trackers finished")
    }

    /**
     * Checks if there are too many notifications in the last 24 hours and sets a hint in SharedPrefs.
     * Conditions:
     * - At least x notifications in the last 24 hours OR
     * - At least y notifications for the same device in the last 24 hours
     */
    private fun checkTooManyNotificationsHint(
        notificationsLastDay: Int = 5,
        notificationsPerDeviceLastDay: Int = 3
    ) {
        val since = LocalDateTime.now().minusHours(24)
        val recentNotifications = notificationRepository.notificationsSince(since)

        // Check if there are at least x notifications in total
        val totalNotifications = recentNotifications.size
        if (totalNotifications == notificationsLastDay) {
            SharedPrefs.showTooManyNotificationsHint = true
            // TODO: Throw Notification
            return
        }

        // Check if there are at least y notifications for the same device
        val notificationsByDevice = recentNotifications.groupBy { it.deviceAddress }
        val maxNotificationsForDevice = notificationsByDevice.values.maxOfOrNull { it.size } ?: 0
        if (maxNotificationsForDevice == notificationsPerDeviceLastDay) {
            SharedPrefs.showTooManyNotificationsHint = true
            // TODO: Throw Notification
            return
        }
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