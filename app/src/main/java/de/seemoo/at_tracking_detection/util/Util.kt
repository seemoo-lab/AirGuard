package de.seemoo.at_tracking_detection.util

import android.graphics.BitmapFactory
import android.view.View
import androidx.core.content.res.ResourcesCompat
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

    fun setGeoPointsFromList(
        beaconList: List<Beacon>,
        view: View,
        connectWithPolyline: Boolean = false,
        onMarkerWindowClick: ((beacon: Beacon) -> Unit)? = null
    ): Boolean {

        val map: MapView = view.findViewById(R.id.map)
        val locationOverlay = MyLocationNewOverlay(map)
        val options = BitmapFactory.Options()

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