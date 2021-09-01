package de.seemoo.at_tracking_detection.util

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.tables.Beacon
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import timber.log.Timber

object Util {

    const val MAX_ZOOM_LEVEL = 19.5

    private fun handlePermissions(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        val builder: AlertDialog.Builder = context.let { AlertDialog.Builder(it) }

        builder.setMessage(R.string.onboarding_2_description)
        builder.setTitle(R.string.onboarding_2_title)
        builder.setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->
            ATTrackingDetectionApplication.getCurrentActivity().requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), 0
            )
        }
        builder.setNegativeButton(context.getString(R.string.no_button), null)

        val dialog = builder.create()
        dialog?.show()
    }


    fun setGeoPointsFromList(
        beaconList: List<Beacon>,
        view: View,
        connectWithPolyline: Boolean = false,
        onMarkerWindowClick: ((beacon: Beacon) -> Unit)? = null
    ): Boolean {

        val map: MapView = view.findViewById(R.id.map)
        val locationOverlay = MyLocationNewOverlay(map)
        val options = BitmapFactory.Options()

        val permissionState =
            ContextCompat.checkSelfPermission(
                view.context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (!permissionState) {
            handlePermissions(view.context)
        }
        val bitmapPerson =
            BitmapFactory.decodeResource(view.resources, R.drawable.mylocation, options)
        locationOverlay.setPersonIcon(bitmapPerson)
        locationOverlay.setPersonHotspot((26.0 * 1.6).toFloat(), (26.0 * 1.6).toFloat())
        val mapController = map.controller
        val geoPointList = ArrayList<GeoPoint>()

        locationOverlay.enableMyLocation()
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setUseDataConnection(true)
        map.setMultiTouchControls(true)
        map.overlays.add(locationOverlay)

        beaconList
            .filter { it.latitude != null && it.longitude != null }
            .map { beacon ->
                val marker = Marker(map)
                val geoPoint = GeoPoint(beacon.longitude!!, beacon.latitude!!)
                marker.infoWindow = DeviceMarkerInfo(
                    R.layout.include_device_marker_window, map, beacon, onMarkerWindowClick
                )
                marker.position = geoPoint
                marker.icon = ResourcesCompat.getDrawable(
                    view.resources,
                    R.drawable.ic_baseline_location_on_45_black,
                    null
                )
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                geoPointList.add(geoPoint)
                map.overlays.add(marker)

                marker.setOnMarkerClickListener { clickedMarker, _ ->
                    if (clickedMarker.isInfoWindowShown) {
                        clickedMarker.closeInfoWindow()
                    } else {
                        clickedMarker.showInfoWindow()
                    }
                    true
                }
            }

        Timber.d("Added ${geoPointList.size} markers to the map!")

        if (connectWithPolyline) {
            val line = Polyline(map)
            line.setPoints(geoPointList)
            line.infoWindow = null
            map.overlays.add(line)
        }
        if (geoPointList.isEmpty()) {
            locationOverlay.enableFollowLocation()
            mapController.setZoom(MAX_ZOOM_LEVEL)
            return false
        }
        val boundingBox = BoundingBox.fromGeoPointsSafe(geoPointList)
        try {
            Timber.d("Zoom in to bounds -> $boundingBox")
            map.zoomToBoundingBox(boundingBox, false, 50, MAX_ZOOM_LEVEL, null)
        } catch (e: IllegalArgumentException) {
            mapController.setCenter(geoPointList.random())
            mapController.setZoom(MAX_ZOOM_LEVEL)
            Timber.e("Failed to zoom to bounding box! ${e.message}")
            return false
        }
        return true
    }
}