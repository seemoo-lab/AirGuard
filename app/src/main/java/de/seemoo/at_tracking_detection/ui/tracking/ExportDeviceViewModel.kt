package de.seemoo.at_tracking_detection.ui.tracking

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ExportDeviceViewModel : ViewModel() {
    val followingStatusText = MutableLiveData<String>()
    var followingStatusColor = MutableLiveData(R.color.warning_light_red)
    val basicInfoText = MutableLiveData("Loading")
    val beaconPreviewList = MutableLiveData<List<BeaconPreviewItem>>()

    val warning15MinAlgoVisible = MutableLiveData(false)

    // Cached Data for the Fragment
    val retrievedDevice = MutableLiveData<BaseDevice?>()
    val retrievedBeacons = MutableLiveData<List<Beacon>>()
    val retrievedLocations = MutableLiveData<List<Location>>()

    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

    fun loadDevice(deviceAddress: String?, context: Context) {
        if (deviceAddress == null) {
            return
        }

        val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
        val device = deviceRepository.getDevice(deviceAddress)
        retrievedDevice.postValue(device)
        val beaconRepository = ATTrackingDetectionApplication.getCurrentApp().beaconRepository
        val beacons = beaconRepository.getDeviceBeacons(deviceAddress)
        retrievedBeacons.postValue(beacons)
        val locationRepository = ATTrackingDetectionApplication.getCurrentApp().locationRepository
        val locations = locationRepository.getLocationsForBeacon(deviceAddress)
        retrievedLocations.postValue(locations)

        if (device != null) {
            warning15MinAlgoVisible.postValue(device.matchedUsing15MinAlgo)

            val trackerFollowing = isTrackerFollowing(device)
            if (trackerFollowing) {
                followingStatusText.postValue(context.getString(R.string.export_trackers_following))
                followingStatusColor.postValue(ContextCompat.getColor(context, R.color.tracker_following_red))
            } else {
                if (device.ignore) {
                    followingStatusText.postValue(context.getString(R.string.export_trackers_ignored))
                } else {
                    followingStatusText.postValue(context.getString(R.string.export_trackers_not_following))
                }
                followingStatusColor.postValue(ContextCompat.getColor(context, R.color.tracker_not_following_blue))

            }

            val deviceTypeStr = DeviceManager.deviceTypeToString(device.deviceType ?: DeviceType.UNKNOWN)
            val lastSeenDate = device.lastSeen.format(dateTimeFormatter)
            val firstSeenDate = device.firstDiscovery.format(dateTimeFormatter)
            val detectionCount = beacons.size
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
            val locations = locationRepository.getLocationsForBeacon(deviceAddress)
            val locationMap = locations.associateBy { it.locationId }

            val previewItems = beacons.map { beacon ->
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

            beaconPreviewList.postValue(previewItems)
        }
    }

    companion object {
        fun isTrackerFollowing(device: BaseDevice): Boolean {
            // Backup in case something with the notification went wrong
            val useLocation = SharedPrefs.useLocationInTrackingDetection
            val deviceRiskLevel = checkRiskLevelForDevice(device, useLocation)

            // This is the actual logic to determine if a tracker is following
            val notificationRepository = ATTrackingDetectionApplication.getCurrentApp().notificationRepository
            val notificationExits = notificationRepository.existsNotificationForDevice(device.address)
            return notificationExits || (deviceRiskLevel != RiskLevel.LOW)
        }
    }
}