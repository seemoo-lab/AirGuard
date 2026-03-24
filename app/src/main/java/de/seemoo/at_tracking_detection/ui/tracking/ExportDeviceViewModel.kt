package de.seemoo.at_tracking_detection.ui.tracking

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Location
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.risk.RiskLevel
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator.Companion.checkRiskLevelForDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ExportDeviceViewModel : ViewModel() {
    val followingStatusText = MutableLiveData<String>()
    var followingStatusColor = MutableLiveData(R.color.md_theme_error)
    val basicInfoText = MutableLiveData("Loading")
    val warning15MinAlgoVisible = MutableLiveData(false)

    val recentBeaconPreviewList = MutableLiveData<List<BeaconPreviewItem>>()
    val secondaryBeaconPreviewList = MutableLiveData<List<BeaconPreviewItem>>()
    val isLoadingRecentBeacons = MutableLiveData(true)
    val isLoadingSecondaryBeacons = MutableLiveData(false)
    val totalBeaconCount = MutableLiveData(0)

    val isLoadMoreButtonVisible = MediatorLiveData<Boolean>().apply {
        addSource(totalBeaconCount) { updateLoadMoreVisibility() }
        addSource(isLoadingRecentBeacons) { updateLoadMoreVisibility() }
        addSource(isLoadingSecondaryBeacons) { updateLoadMoreVisibility() }
        addSource(secondaryBeaconPreviewList) { updateLoadMoreVisibility() }
    }

    private fun updateLoadMoreVisibility() {
        val total = totalBeaconCount.value ?: 0
        val loadingRecent = isLoadingRecentBeacons.value ?: false
        val loadingSecondary = isLoadingSecondaryBeacons.value ?: false
        val secondaryLoaded = (secondaryBeaconPreviewList.value?.size ?: 0) > 0

        isLoadMoreButtonVisible.value = !loadingRecent && !loadingSecondary && total > 20 && !secondaryLoaded
    }

    // Cached Data for the Fragment
    val retrievedDevice = MutableLiveData<BaseDevice?>()
    val retrievedBeacons = MutableLiveData<List<Beacon>>()
    val retrievedLocations = MutableLiveData<List<Location>>()

    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

    fun loadDevice(deviceAddress: String?, context: Context) {
        if (deviceAddress == null) {
            return
        }

        isLoadingRecentBeacons.postValue(true)

        viewModelScope.launch(Dispatchers.IO) {
            val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository
                ?: error("ATTrackingDetectionApplication not initialized")
            val device = deviceRepository.getDevice(deviceAddress)
            retrievedDevice.postValue(device)

            // Load only most recent 10 beacons here
            val beaconRepository = ATTrackingDetectionApplication.getCurrentApp()?.beaconRepository
                ?: error("ATTrackingDetectionApplication not initialized")
            val recentBeacons = beaconRepository.getRecentDeviceBeacons(deviceAddress, 10)

            val totalCount = beaconRepository.getDeviceBeaconsCount(deviceAddress)
            totalBeaconCount.postValue(totalCount)

            val locationRepository = ATTrackingDetectionApplication.getCurrentApp()?.locationRepository
                ?: error("ATTrackingDetectionApplication not initialized")
            val locations = locationRepository.getLocationsForBeacon(deviceAddress)
            retrievedLocations.postValue(locations)

            if (device != null) {
                warning15MinAlgoVisible.postValue(device.matchedUsing15MinAlgo)

                val trackerFollowing = isTrackerFollowing(device)
                if (trackerFollowing) {
                    followingStatusText.postValue(context.getString(R.string.export_trackers_following))
                    followingStatusColor.postValue(ContextCompat.getColor(context, R.color.md_theme_error))
                } else {
                    if (device.ignore) {
                        followingStatusText.postValue(context.getString(R.string.export_trackers_ignored))
                        followingStatusColor.postValue(ContextCompat.getColor(context, R.color.md_theme_secondary))
                    } else {
                        followingStatusText.postValue(context.getString(R.string.export_trackers_not_following))
                        followingStatusColor.postValue(ContextCompat.getColor(context, R.color.md_theme_primary))
                    }
                }

                val deviceTypeStr = DeviceManager.deviceTypeToString(device.deviceType ?: DeviceType.UNKNOWN)
                val lastSeenDate = device.lastSeen.format(dateTimeFormatter)
                val firstSeenDate = device.firstDiscovery.format(dateTimeFormatter)
                val detectionCount = totalCount // Use total count from DB
                val uniqueLocationCount = locations.size

                val infoBuilder = StringBuilder()

                infoBuilder.append(context.getString(R.string.export_trackers_tracker_type, deviceTypeStr)).append("\n")
                if (device.deviceType != DeviceType.SAMSUNG_TRACKER && device.deviceType != DeviceType.SAMSUNG_FIND_MY_MOBILE) {
                    infoBuilder.append(context.getString(R.string.export_trackers_mac, device.address)).append("\n")
                }
                infoBuilder.append(context.getString(R.string.export_trackers_first_seen, firstSeenDate)).append("\n")
                infoBuilder.append(context.getString(R.string.export_trackers_last_seen, lastSeenDate)).append("\n")
                infoBuilder.append(context.getString(R.string.export_trackers_detections, detectionCount)).append("\n")
                infoBuilder.append(context.getString(R.string.export_trackers_unique_locations, uniqueLocationCount))

                basicInfoText.postValue(infoBuilder.toString())

                // This creates a map of locations for faster lookups
                val locationMap = locations.associateBy { it.locationId }

                val previewItems = mapBeaconsToPreview(recentBeacons, locationMap, context)

                recentBeaconPreviewList.postValue(previewItems)
                isLoadingRecentBeacons.postValue(false)
            }
        }
    }

    fun loadAllBeacons(deviceAddress: String?, context: Context) {
        if (deviceAddress == null) return

        isLoadingSecondaryBeacons.postValue(true)

        viewModelScope.launch(Dispatchers.IO) {
            val beaconRepository = ATTrackingDetectionApplication.getCurrentApp()?.beaconRepository
                ?: error("ATTrackingDetectionApplication not initialized")
            val allBeacons = beaconRepository.getDeviceBeacons(deviceAddress)

            // The secondary list contains everything after the first 10 preview items
            val restBeacons = if (allBeacons.size > 10) allBeacons.subList(10, allBeacons.size) else emptyList()

            val locationRepository = ATTrackingDetectionApplication.getCurrentApp()?.locationRepository
                ?: error("ATTrackingDetectionApplication not initialized")
            val locations = locationRepository.getLocationsForBeacon(deviceAddress)
            val locationMap = locations.associateBy { it.locationId }

            val previewItems = mapBeaconsToPreview(restBeacons, locationMap, context)

            secondaryBeaconPreviewList.postValue(previewItems)
            retrievedBeacons.postValue(allBeacons)
            isLoadingSecondaryBeacons.postValue(false)
        }
    }

    private fun mapBeaconsToPreview(beacons: List<Beacon>, locationMap: Map<Int, Location>, context: Context): List<BeaconPreviewItem> {
        return beacons.map { beacon ->
            val beaconLocation = locationMap[beacon.locationId]
            val timeStr = context.getString(R.string.export_trackers_time, beacon.receivedAt.format(dateTimeFormatter))
            val locationStr = if (beaconLocation != null) {
                context.getString(R.string.export_trackers_location, "%.4f".format(beaconLocation.latitude), "%.4f".format(beaconLocation.longitude))
            } else {
                context.getString(R.string.export_trackers_location_unknown)
            }
            BeaconPreviewItem(
                id = beacon.beaconId,
                timeText = timeStr,
                locationText = locationStr
            )
        }
    }

    companion object {
        fun isTrackerFollowing(device: BaseDevice): Boolean {
            // Backup in case something with the notification went wrong
            val useLocation = SharedPrefs.useLocationInTrackingDetection
            val deviceRiskLevel = checkRiskLevelForDevice(device, useLocation)

            // This is the actual logic to determine if a tracker is following
            val notificationRepository = ATTrackingDetectionApplication.getCurrentApp()?.notificationRepository
                ?: error("ATTrackingDetectionApplication not initialized")
            val notificationExits = notificationRepository.existsNotificationForDevice(device.address)
            return notificationExits || (deviceRiskLevel != RiskLevel.LOW)
        }
    }
}