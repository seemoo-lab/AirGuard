package de.seemoo.at_tracking_detection.util

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Location
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.ui.OnboardingActivity
import de.seemoo.at_tracking_detection.util.ble.DbmToPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.bonuspack.utils.BonusPackHelper
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
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
        val locationOverlay = MyLocationNewOverlay(map)
//        val context = ATTrackingDetectionApplication.getAppContext()
//        val options = BitmapFactory.Options()
//        val bitmapPerson =
//            BitmapFactory.decodeResource(context.resources, R.drawable.mylocation, options)
//        locationOverlay.setPersonIcon(bitmapPerson)
//        locationOverlay.setPersonHotspot((26.0 * 1.6).toFloat(), (26.0 * 1.6).toFloat())
//        locationOverlay.setDirectionArrow(bitmapPerson, bitmapPerson)
//        locationOverlay.enableMyLocation()
//        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)
        map.controller.setZoom(ZOOMED_OUT_LEVEL)
    }

    suspend fun setGeoPointsFromListOfLocations(
        locationList: List<Location>,
        map: MapView,
    ): Boolean {
        val context = ATTrackingDetectionApplication.getAppContext()
        val copyrightOverlay = CopyrightOverlay(context)

        val mapController = map.controller
        val geoPointList = ArrayList<GeoPoint>()

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setUseDataConnection(true)
        map.setMultiTouchControls(true)

        map.overlays.add(copyrightOverlay)

        val iconDrawable = R.drawable.ic_baseline_location_on_45_black

        val clusterer = RadiusMarkerClusterer(context)
        val clusterIcon = BonusPackHelper.getBitmapFromVectorDrawable(context, iconDrawable)
        clusterer.setIcon(clusterIcon)
        clusterer.setRadius(100)
        clusterer.mAnchorU = Marker.ANCHOR_CENTER
        clusterer.mAnchorV = Marker.ANCHOR_BOTTOM
        clusterer.mTextAnchorV = 0.6f

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
                        iconDrawable
                    )
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    geoPointList.add(geoPoint)

                    marker.setOnMarkerClickListener { clickedMarker, _ ->
                        clickedMarker.closeInfoWindow()
                        false
                    }

                    clusterer.add(marker)
                }
        }

        map.overlays.add(clusterer)
        Timber.d("Added ${geoPointList.size} markers to the map!")

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

    fun getSensitivity(): Int {
        return when (SharedPrefs.riskSensitivity) {
            "low" -> 1
            "medium" -> 2
            "high" -> 3
            else -> 0
        }
    }

    fun connectionStateToString(connectionState: ConnectionState): String {
        return when (connectionState) {
            ConnectionState.CONNECTED -> "CONNECTED"
            ConnectionState.OFFLINE -> "OFFLINE"
            ConnectionState.OVERMATURE_OFFLINE -> "OVERMATURE_OFFLINE"
            ConnectionState.PREMATURE_OFFLINE -> "PREMATURE_OFFLINE"
            ConnectionState.UNKNOWN -> "UNKNOWN"
        }
    }

    fun getExplanationTextForDeviceType(deviceType: DeviceType?): String {
        Timber.d("get Explanation for DeviceType: $deviceType")
        return when (deviceType) {
            DeviceType.APPLE -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_apple)
            DeviceType.AIRPODS -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_apple)
            DeviceType.FIND_MY -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_apple)
            DeviceType.AIRTAG-> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_apple)
            DeviceType.SAMSUNG -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_samsung)
            DeviceType.GALAXY_SMART_TAG -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_samsung)
            DeviceType.GALAXY_SMART_TAG_PLUS -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_samsung)
            DeviceType.TILE -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_tile)
            DeviceType.CHIPOLO -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_chipolo)
            else -> ""
        }
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