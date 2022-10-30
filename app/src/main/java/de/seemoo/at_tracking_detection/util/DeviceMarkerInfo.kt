package de.seemoo.at_tracking_detection.util

import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Beacon
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.infowindow.InfoWindow
import timber.log.Timber

class DeviceMarkerInfo( // TODO: modify for new location model
    layoutResId: Int,
    map: MapView,
    private val beacon: Beacon,
    private val onMarkerWindowClick: ((beacon: Beacon) -> Unit)? = null
) :
    InfoWindow(layoutResId, map) {

    override fun onOpen(item: Any?) {
        Timber.d("Showing info window for beacon ${beacon.beaconId}")
        val card = mView.findViewById<MaterialCardView>(R.id.device_marker_window_card)
        val deviceAddress = mView.findViewById<TextView>(R.id.device_marker_window_address)
        val rssi = mView.findViewById<TextView>(R.id.device_marker_window_rssi)
        val datetime = mView.findViewById<TextView>(R.id.device_marker_window_datetime)
        deviceAddress.text = beacon.deviceAddress
        rssi.text = beacon.rssi.toString()
        datetime.text = beacon.getFormattedDate()
        if (onMarkerWindowClick != null) {
            card.setOnClickListener {
                onMarkerWindowClick.invoke(beacon)
            }
        }
    }

    override fun onClose() {
        close()
    }
}