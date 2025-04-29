package de.seemoo.at_tracking_detection.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.app.ActivityCompat.startActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.snackbar.Snackbar
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Location
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.ui.OnboardingActivity
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.ble.DbmToPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
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
import java.io.File
import java.io.FileWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Utility {

    private const val MAX_ZOOM_LEVEL = 18.0
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

    fun checkPermission(permission: String): Boolean {
        val context = ATTrackingDetectionApplication.getCurrentActivity() ?: return false
        return ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun checkBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(
            ATTrackingDetectionApplication.getAppContext(),
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getBitsFromByte(value: Byte, position: Int): Boolean {
        return ((value.toInt() shr position) and 1) == 1
    }

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

    suspend fun setGeoPointsFromListOfLocations(
        locationList: List<Location>,
        map: MapView,
    ): Boolean {
        val context = ATTrackingDetectionApplication.getAppContext()

        val mapController = map.controller
        val geoPointList = ArrayList<GeoPoint>()

        val icon = R.drawable.ic_baseline_location_on_45_black
        val iconDrawable = ContextCompat.getDrawable(
            context, icon

        )

        val clusterer = RadiusMarkerClusterer(context)
        val clusterIcon = BonusPackHelper.getBitmapFromVectorDrawable(context, icon)
        clusterer.setIcon(clusterIcon)
        clusterer.setRadius(100)
        clusterer.mAnchorU = Marker.ANCHOR_CENTER
        clusterer.mAnchorV = Marker.ANCHOR_BOTTOM
        clusterer.mTextAnchorV = 0.6f

        withContext(Dispatchers.Default) {
            locationList
                .filter { it.locationId != 0 }
                .forEach { location ->
                    val marker = Marker(map)
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    marker.position = geoPoint
                    marker.icon = iconDrawable
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
            map.post { map.invalidate() }
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

    fun isActualThemeDark(context: Context): Boolean {
        return when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true // App theme explicitly set to Dark
            AppCompatDelegate.MODE_NIGHT_NO -> false // App theme explicitly set to Light
            else -> { // App theme set to Follow System or other modes (e.g., Battery Saver)
                val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == Configuration.UI_MODE_NIGHT_YES // Return true if the system configuration is currently dark
            }
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

    fun getConnectionStateFromString(connectionState: String): ConnectionState {
        return when (connectionState) {
            "CONNECTED" -> ConnectionState.CONNECTED
            "OFFLINE" -> ConnectionState.OFFLINE
            "OVERMATURE_OFFLINE" -> ConnectionState.OVERMATURE_OFFLINE
            "PREMATURE_OFFLINE" -> ConnectionState.PREMATURE_OFFLINE
            "UNKNOWN" -> ConnectionState.UNKNOWN
            else -> ConnectionState.UNKNOWN
        }
    }

    fun getExplanationTextForDeviceType(deviceType: DeviceType?): String {
        Timber.d("get Explanation for DeviceType: $deviceType")
        return when (deviceType) {
            DeviceType.APPLE,
            DeviceType.AIRPODS,
            DeviceType.FIND_MY,
            DeviceType.AIRTAG -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_apple)
            DeviceType.SAMSUNG_TRACKER -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_samsung)
            DeviceType.TILE -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_tile)
            DeviceType.CHIPOLO -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_chipolo)
            DeviceType.PEBBLEBEE -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_pebblebee)
            else -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.explanation_unknown)
        }
    }

    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectAndRetrieveCharacteristics(
        context: Context,
        deviceAddress: String,
        characteristicsToRead: List<Triple<UUID, UUID, String>> // Third value: "string", "int", or "hex"
    ): Map<UUID, Any?> = suspendCancellableCoroutine { continuation ->
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)

        val resultsMap = mutableMapOf<UUID, Any?>()
        var currentCharacteristicIndex = 0

        val gattCallback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Timber.d("Connected to GATT server.")
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Timber.d("Disconnected from GATT server.")
                    if (continuation.isActive) {
                        continuation.resume(resultsMap)
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Timber.d("Services discovered.")
                    gatt?.let {
                        readNextCharacteristic(it)
                    }
                } else {
                    Timber.w("onServicesDiscovered received: $status")
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception("Failed to discover services: $status"))
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            @SuppressLint("MissingPermission")
            override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    characteristic?.let {
                        val dataType = characteristicsToRead[currentCharacteristicIndex].third
                        val value = when (dataType) {
                            "int" -> it.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0) // Example: 16-bit unsigned integer
                            "string" -> it.getStringValue(0) // Read as a string
                            "hex" -> it.value.joinToString("") { byte -> "%02x".format(byte) } // Convert to hex string
                            else -> null
                        }
                        resultsMap[it.uuid] = value
                    }
                } else {
                    Timber.w("Failed to read characteristic: $status")
                }
                gatt?.let { readNextCharacteristic(it) }
            }

            @SuppressLint("MissingPermission")
            private fun readNextCharacteristic(gatt: BluetoothGatt) {
                if (currentCharacteristicIndex < characteristicsToRead.size) {
                    val (serviceUUID, characteristicUUID, _) = characteristicsToRead[currentCharacteristicIndex]
                    val service = gatt.getService(serviceUUID)
                    val char = service?.getCharacteristic(characteristicUUID)

                    if (char != null) {
                        gatt.readCharacteristic(char)
                    } else {
                        resultsMap[characteristicUUID] = "Unknown"
                        currentCharacteristicIndex++
                        readNextCharacteristic(gatt)
                    }
                } else {
                    if (continuation.isActive) {
                        continuation.resume(resultsMap)
                    }
                    gatt.disconnect()
                }
            }
        }

        bluetoothDevice.connectGatt(context, false, gattCallback)
    }

    suspend fun isValidURL(url: URL): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            val responseCode = connection.responseCode
            Timber.d("Response code: $responseCode")
            responseCode < 400
        } catch (e: Exception) {
            Timber.e("Error checking URL: ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun openBrowser(context: Context, url: String, view: View) {
        Timber.d("Opening browser with URL: $url")
        val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "http://$url"
        } else {
            url
        }

        val intent = Intent(Intent.ACTION_VIEW, finalUrl.toUri())

        // Check if there's an app to handle this intent
        try {
            startActivity(context, intent, null)
        } catch (e: Exception) {
            Timber.e("Error opening browser: ${e.localizedMessage}")
            Snackbar.make(
                view,
                R.string.retrieve_owner_information_failed,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    fun getSkipDevice(wrappedScanResult: ScanResultWrapper) : Boolean {
        val deviceType = wrappedScanResult.deviceType
        val securityLevel = SharedPrefs.riskSensitivity

        if (deviceType == DeviceType.SAMSUNG_FIND_MY_MOBILE) {
            return securityLevel != "high"
        }

        return false
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

    object LocationLogger {
        private const val LOG_FILE_NAME = "location_log.log"
        val loggingTurnedOn: Boolean = BuildConfig.DEBUG

        fun log(message: String) {
            if (!loggingTurnedOn) return

            // Timber.d(message)

            val context = ATTrackingDetectionApplication.getAppContext()
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            try {
                FileWriter(logFile, true).use { writer ->
                    writer.appendLine("${LocalDateTime.now()}: $message")
                }
            } catch (e: Exception) {
                Timber.e("Failed to write to log file: ${e.message}")
            }
        }
    }
}