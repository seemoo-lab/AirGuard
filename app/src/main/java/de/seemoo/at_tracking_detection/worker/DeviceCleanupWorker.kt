package de.seemoo.at_tracking_detection.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import timber.log.Timber

@HiltWorker
class DeviceCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val deviceRepository: DeviceRepository,
    private val beaconRepository: BeaconRepository,
    private val notificationRepository: NotificationRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("DeviceCleanupWorker started")

        try {
            deleteSafeGoogleTrackers()
            deleteOldAndSafeTrackers()
        } catch (e: Exception) {
            Timber.e("DeviceCleanupWorker failed: $e")
        }

        Timber.d("DeviceCleanupWorker finished")
        return Result.success()
    }

    private suspend fun deleteSafeGoogleTrackers() {
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
                // Delete notifications for the deleted devices
                devicesToBeDeleted.forEach { device ->
                    notificationRepository.deleteForDevice(device.address)
                }
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
}

