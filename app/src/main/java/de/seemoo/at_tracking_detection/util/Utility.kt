package de.seemoo.at_tracking_detection.util

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Location as LocationModel
import de.seemoo.at_tracking_detection.ui.OnboardingActivity
import de.seemoo.at_tracking_detection.util.ble.DbmToPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import timber.log.Timber

object Utility {

    private const val MAX_ZOOM_LEVEL = 19.5
    private const val ZOOMED_OUT_LEVEL = 15.0

    fun checkAndRequestPermission(permission: String): Boolean {
        val context = ATTrackingDetectionApplication.getCurrentActivity() ?: return false
        when {
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                return true
            }
            shouldShowRequestPermissionRationale(context, permission) -> {
                val bundle = Bundle().apply { putString("permission", permission) }
                val intent = Intent(context, OnboardingActivity::class.java).apply {
                    putExtras(bundle)
                }
                context.startActivity(intent)
                return false
            }
            else -> {
                requestPermissions(
                    context,
                    arrayOf(permission),
                    0
                )
                return true
            }
        }
    }

    fun checkBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(
            ATTrackingDetectionApplication.getAppContext(),
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getBitsFromByte(value: Byte, position: Int): Boolean {
        // This uses Little Endian
        return ((value.toInt() shr position) and 1) == 1
    }

    fun enableMyLocationOverlay(
        map: MapView
    ) {
        val context = ATTrackingDetectionApplication.getAppContext()
        val locationOverlay = MyLocationNewOverlay(map)
        val options = BitmapFactory.Options()
        val bitmapPerson =
            BitmapFactory.decodeResource(context.resources, R.drawable.mylocation, options)
        locationOverlay.setPersonIcon(bitmapPerson)
        locationOverlay.setPersonHotspot((26.0 * 1.6).toFloat(), (26.0 * 1.6).toFloat())
        locationOverlay.setDirectionArrow(bitmapPerson, bitmapPerson)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)
        map.controller.setZoom(ZOOMED_OUT_LEVEL)
    }

    suspend fun setGeoPointsFromListOfLocations(
        locationList: List<LocationModel>,
        map: MapView,
        connectWithPolyline: Boolean = false,
    ): Boolean {
        val context = ATTrackingDetectionApplication.getAppContext()
        val copyrightOverlay = CopyrightOverlay(context)

        val mapController = map.controller
        val geoPointList = ArrayList<GeoPoint>()
        val markerList = ArrayList<Marker>()

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setUseDataConnection(true)
        map.setMultiTouchControls(true)

        map.overlays.add(copyrightOverlay)

        withContext(Dispatchers.Default) {
            locationList
                .filter { it.locationId != 0 }
                .forEach { location ->
                    if (!map.isShown) {
                        return@forEach
                    }

                    val marker = Marker(map)
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    marker.position = geoPoint
                    marker.icon = ContextCompat.getDrawable(
                        context,
                        R.drawable.ic_baseline_location_on_45_black
                    )
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    geoPointList.add(geoPoint)
                    markerList.add(marker)

                    marker.setOnMarkerClickListener { clickedMarker, _ ->
                        clickedMarker.closeInfoWindow()
                        false
                    }
                }
        }

        map.overlays.addAll(markerList)
        Timber.d("Added ${geoPointList.size} markers to the map!")

        if (connectWithPolyline) {
            val line = Polyline(map)
            line.setPoints(geoPointList)
            line.infoWindow = null
            map.overlays.add(line)
        }

        if (geoPointList.isEmpty()) {
            mapController.setZoom(MAX_ZOOM_LEVEL)
            return false
        }

        val myLocationOverlay =
            map.overlays.firstOrNull { it is MyLocationNewOverlay } as? MyLocationNewOverlay
        myLocationOverlay?.disableFollowLocation()
        val boundingBox = BoundingBox.fromGeoPointsSafe(geoPointList)

        map.post {
            try {
                Timber.d("Zoom in to bounds -> $boundingBox")
                map.zoomToBoundingBox(boundingBox, true, 100, MAX_ZOOM_LEVEL, 1)
            } catch (e: IllegalArgumentException) {
                mapController.setCenter(boundingBox.centerWithDateLine)
                mapController.setZoom(10.0)
                Timber.e("Failed to zoom to bounding box! ${e.message}")
            }
        }

        return true
    }


    fun setSelectedTheme(sharedPreferences: SharedPreferences) {
        when (sharedPreferences.getString("app_theme", "system_default")) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system_default" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun getSelectedTheme(): Boolean {
        return when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> false // Dark mode
            else -> true // Light mode or system default
        }
    }

    fun dbmToQuality(rssi: Int): Int {
        val percentage = dbmToPercent(rssi)
        return rssiToQuality(percentage.toFloat())
    }

    fun dbmToPercent(rssi: Int, perfectRssi: Double = -30.0, worstRssi: Double = -90.0): Double {
        return DbmToPercent.convert(rssi.toDouble(), perfectRssi = perfectRssi, worstRssi = worstRssi).toDouble() / 100.0
    }

    private fun rssiToQuality(percentage: Float): Int {
        return when (percentage) {
            in 0.75..1.0 -> {
                3
            }
            in 0.5..0.75 -> {
                2
            }
            in 0.25..0.5 -> {
                1
            }
            else -> {
                0
            }
        }
    }
}