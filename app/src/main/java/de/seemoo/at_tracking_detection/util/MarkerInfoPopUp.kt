package de.seemoo.at_tracking_detection.util

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTrackerType
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.infowindow.InfoWindow
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MarkerInfoPopUp(
    mapView: MapView,
    private val locationId: Int,
    private val onMarkerClick: (String) -> Unit,
    private val onMoreTrackersClick: (Int) -> Unit
) : InfoWindow(R.layout.marker_info_window, mapView) {

    override fun onOpen(item: Any?) {
        val context = mView.context
        val beaconRepository = ATTrackingDetectionApplication.getCurrentApp().beaconRepository
        val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository

        // Get the most recent beacon at this location
        val beacon = beaconRepository.getMostRecentBeaconAtLocation(locationId)

        if (beacon == null) {
            close()
            return
        }

        // Get the device information
        val device = deviceRepository.getDevice(beacon.deviceAddress)

        if (device == null) {
            close()
            return
        }

        // Get device count at this location
        val relevantTrackingDate = de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator.relevantTrackingDateForRiskCalculation
        val deviceCount = deviceRepository.getDeviceCountAtLocation(locationId, relevantTrackingDate)
        val additionalDevices = deviceCount - 1

        // Set device icon - clear any tint to show the original drawable
        val deviceIcon = mView.findViewById<ImageView>(R.id.marker_device_icon)
        val drawable = device.getDrawable()
        deviceIcon.setImageDrawable(drawable)

        // Set device name
        val deviceName = mView.findViewById<TextView>(R.id.marker_device_name)
        val displayName = if (device.name != null) {
            device.getDeviceNameWithID()
        } else if (device.deviceType == DeviceType.SAMSUNG_TRACKER && device.subDeviceType != "UNKNOWN") {
            val subType = SamsungTrackerType.stringToSubType(device.subDeviceType)
            SamsungTrackerType.visibleStringFromSubtype(subType)
        } else if (device.deviceType == DeviceType.GOOGLE_FIND_MY_NETWORK && device.subDeviceType != "UNKNOWN") {
            val subType = GoogleFindMyNetworkType.stringToSubType(device.subDeviceType)
            GoogleFindMyNetworkType.visibleStringFromSubtype(subType)
        } else {
            device.deviceType?.let { DeviceType.userReadableNameDefault(it) } ?: "Unknown Device"
        }
        deviceName.text = displayName

        // Set last seen time
        val lastSeenText = mView.findViewById<TextView>(R.id.marker_last_seen)
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        val formattedDate = device.lastSeen.format(formatter)
        lastSeenText.text = context.getString(R.string.export_trackers_last_seen, formattedDate)

        // Show additional trackers if more than one
        val divider = mView.findViewById<View>(R.id.marker_divider)
        val moreTrackersText = mView.findViewById<TextView>(R.id.marker_more_trackers)

        if (additionalDevices > 0) {
            divider.visibility = View.VISIBLE
            moreTrackersText.visibility = View.VISIBLE
            moreTrackersText.text = context.getString(
                R.string.more_trackers_found,
                additionalDevices
            )

            // Set click listener for the "+x more trackers" text
            moreTrackersText.setOnClickListener {
                onMoreTrackersClick(locationId)
                close()
            }
        } else {
            divider.visibility = View.GONE
            moreTrackersText.visibility = View.GONE
        }

        // Set click listener to navigate to tracking fragment
        mView.setOnClickListener {
            onMarkerClick(device.address)
            close()
        }
    }

    override fun onClose() {
        // Clear click listeners
        mView.setOnClickListener(null)
        val moreTrackersText = mView.findViewById<TextView>(R.id.marker_more_trackers)
        moreTrackersText?.setOnClickListener(null)
    }
}

