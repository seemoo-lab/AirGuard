package de.seemoo.at_tracking_detection.util

import android.graphics.BitmapFactory
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Location
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.bonuspack.utils.BonusPackHelper
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import timber.log.Timber

object MapUtils {

    private const val CIRCLE_VISIBILITY_ZOOM_THRESHOLD = 16.0
    private const val LOCATION_CLUSTER_RADIUS_METERS: Double = BackgroundBluetoothScanner.MAX_DISTANCE_UNTIL_NEW_LOCATION.toDouble()
    private const val MAX_ZOOM_LEVEL = 18.0
    private const val ZOOMED_OUT_LEVEL = 15.0

    fun enableMyLocationOverlay(
        map: MapView
    ) {
        val locationOverlay = MyLocationNewOverlay(map)
        val context = ATTrackingDetectionApplication.getAppContext()
        val options = BitmapFactory.Options()
        val bitmapPerson = BitmapFactory.decodeResource(context.resources, R.drawable.mylocation, options)
        locationOverlay.setPersonIcon(bitmapPerson)
        locationOverlay.setPersonHotspot((26.0 * 1.6).toFloat(), (26.0 * 1.6).toFloat())
        locationOverlay.setDirectionArrow(bitmapPerson, bitmapPerson)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)
        map.controller.setZoom(ZOOMED_OUT_LEVEL)
    }

    fun basicMapSetup(map: MapView) {
        val context = ATTrackingDetectionApplication.getAppContext()
        val copyrightOverlay = CopyrightOverlay(context)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setUseDataConnection(true)
        map.setMultiTouchControls(true)
        map.maxZoomLevel = MAX_ZOOM_LEVEL

        map.overlays.add(copyrightOverlay)
    }

    fun fetchLocationListFromBeaconList(locations: List<Beacon>): List<Location> {
        val uniqueLocations = locations
            .distinctBy { it.locationId } // Filter out duplicates based on locationId
            .filter { it.locationId != null && it.locationId != 0 } // Filter out invalid locationId entries

        val locationList = arrayListOf<Location>()
        val locationRepository = ATTrackingDetectionApplication.getCurrentApp().locationRepository

        uniqueLocations.mapNotNullTo(locationList) {
            locationRepository.getLocationWithId(it.locationId!!)
        }

        return locationList
    }

    // Helper to run actions only after the MapView is loaded (has non-zero size)
    private fun runWhenMapReady(map: MapView, action: () -> Unit) {
        if (map.width > 0 && map.height > 0) {
            map.post { action() }
            return
        }
        map.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (map.width > 0 && map.height > 0) {
                    map.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    map.post { action() }
                }
            }
        })
    }

    fun setGeoPointsFromListOfLocations(
        locationList: List<Location>,
        map: MapView,
        showDeviceInfoOnClick: Boolean = false,
        onMarkerClick: ((String) -> Unit)? = null,
        onMoreTrackersClick: ((Int) -> Unit)? = null
    ): Boolean {
        val context = ATTrackingDetectionApplication.getAppContext()
        val mapController = map.controller
        val geoPointList = ArrayList<GeoPoint>()

        val icon = R.drawable.ic_baseline_location_on_45_black
        val iconDrawable = ContextCompat.getDrawable(context, icon)

        // 1. Cleanup existing overlays
        map.overlays.removeAll { it is RadiusMarkerClusterer }
        map.overlays.removeAll { it is Polygon && it.subDescription == "LOCATION_RADIUS_CIRCLE" }
        map.overlays.removeAll { it is MapEventsOverlay }

        // 2. Add Background Click Listener (Fixes Problem #2)
        // We add this at the bottom of the stack (index 0) or before markers
        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                // Close all info windows when tapping empty map space
                InfoWindow.closeAllInfoWindowsOn(map)
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                return false
            }
        })
        map.overlays.add(mapEventsOverlay)

        // 3. Setup Clusterer
        val clusterer = RadiusMarkerClusterer(context)
        val clusterIcon = BonusPackHelper.getBitmapFromVectorDrawable(context, icon)
        clusterer.setIcon(clusterIcon)
        clusterer.setRadius(100)
        clusterer.mAnchorU = Marker.ANCHOR_CENTER
        clusterer.mAnchorV = Marker.ANCHOR_BOTTOM
        clusterer.mTextAnchorV = 0.6f

        val circles = mutableListOf<Polygon>()

        // Helper for circles (same as your original logic)
        fun updateCircleVisibility() {
            val currentZoom = map.zoomLevelDouble
            val shouldShowCircles = currentZoom >= CIRCLE_VISIBILITY_ZOOM_THRESHOLD

            if (!shouldShowCircles) {
                circles.forEach { it.isEnabled = false; it.isVisible = false }
                return
            }

            val boundingBox = map.boundingBox
            val latMargin = (boundingBox.latNorth - boundingBox.latSouth) * 0.2
            val lonMargin = (boundingBox.lonEast - boundingBox.lonWest) * 0.2
            val expandedBoundingBox = BoundingBox(
                boundingBox.latNorth + latMargin,
                boundingBox.lonEast + lonMargin,
                boundingBox.latSouth - latMargin,
                boundingBox.lonWest - lonMargin
            )

            circles.forEach { circle ->
                val centerPoint = circle.actualPoints.firstOrNull()
                val isInCurrentFrame = centerPoint?.let { expandedBoundingBox.contains(it) } ?: false
                circle.isEnabled = isInCurrentFrame
                circle.isVisible = isInCurrentFrame
            }
        }

        locationList
            .filter { it.locationId != 0 }
            .forEach { location ->
                val marker = Marker(map)
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                marker.position = geoPoint
                marker.icon = iconDrawable
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                geoPointList.add(geoPoint)

                if (showDeviceInfoOnClick) {
                    val infoWindow = MarkerInfoPopUp(map, location.locationId,
                        onMarkerClick = { deviceAddress ->
                            onMarkerClick?.invoke(deviceAddress)
                        },
                        onMoreTrackersClick = { locationId ->
                            onMoreTrackersClick?.invoke(locationId)
                        }
                    )
                    marker.infoWindow = infoWindow

                    // Fix for Problem #1: Close others, open this one
                    marker.setOnMarkerClickListener { clickedMarker, _ ->
                        if (clickedMarker.isInfoWindowShown) {
                            clickedMarker.closeInfoWindow()
                        } else {
                            // Helper provided by osmdroid to close all windows on this map
                            InfoWindow.closeAllInfoWindowsOn(map)
                            clickedMarker.showInfoWindow()
                        }
                        true
                    }
                } else {
                    marker.setOnMarkerClickListener { clickedMarker, _ ->
                        clickedMarker.closeInfoWindow()
                        false
                    }
                }

                clusterer.add(marker)

                // Circle setup
                val circle = Polygon(map).apply {
                    points = Polygon.pointsAsCircle(geoPoint, LOCATION_CLUSTER_RADIUS_METERS)
                    fillColor = 0x2034A7FF
                    strokeColor = 0x5534A7FF
                    strokeWidth = 2f
                    subDescription = "LOCATION_RADIUS_CIRCLE"
                    isVisible = map.zoomLevelDouble >= CIRCLE_VISIBILITY_ZOOM_THRESHOLD
                    isEnabled = map.zoomLevelDouble >= CIRCLE_VISIBILITY_ZOOM_THRESHOLD
                    infoWindow = null
                }
                circles.add(circle)
                map.overlays.add(circle)
            }

        // Map Listeners
        map.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                updateCircleVisibility()
                return false
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                updateCircleVisibility()
                return true
            }
        })

        map.overlays.add(clusterer)
        Timber.d("Added ${geoPointList.size} markers to the map!")

        if (geoPointList.isEmpty()) {
            mapController.setZoom(MAX_ZOOM_LEVEL)
            map.post { map.invalidate() }
            return false
        }

        val boundingBox = BoundingBox.fromGeoPointsSafe(geoPointList)

        // Zoom logic
        if (map.width > 0 && map.height > 0) {
            zoomToBounds(map, boundingBox)
        } else {
            map.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (map.width > 0 && map.height > 0) {
                        map.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        zoomToBounds(map, boundingBox)
                    }
                }
            })
        }

        return true
    }

    private fun zoomToBounds(map: MapView, boundingBox: BoundingBox) {
        try {
            map.zoomToBoundingBox(boundingBox, true, 100, MAX_ZOOM_LEVEL, 1)
        } catch (e: IllegalArgumentException) {
            map.controller.setCenter(boundingBox.centerWithDateLine)
            map.controller.setZoom(10.0)
        }
        map.invalidate()
    }
}

