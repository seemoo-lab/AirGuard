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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.ui.OnboardingActivity
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.ble.DbmToPercent
import fr.bipi.treessence.file.FileLoggerTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.round

object Utility {
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

    // TODO: at some point, refactor application, so it uses proper color schemes everywhere instead of this function
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

    /**
     * Risk sensitivity (security level) used for the statistics database
     */
    fun getSensitivityLevelValue(): Int {
        return when (SharedPrefs.riskSensitivity) {
            "low" -> 0
            "medium" -> 1
            "high" -> 2
            else -> -1
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

        // Forward declaration of gatt
        var gatt: BluetoothGatt? = null

        val gattCallback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            private fun closeGatt() {
                gatt?.close()
                gatt = null
            }

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
                    closeGatt()
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Timber.d("Services discovered.")
                    gatt?.let { readNextCharacteristic(it) }
                } else {
                    Timber.w("onServicesDiscovered received: $status")
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception("Failed to discover services: $status"))
                    }
                    gatt?.disconnect()
                    closeGatt()
                }
            }

            @Deprecated("Deprecated in Java")
            @SuppressLint("MissingPermission")
            override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                super.onCharacteristicRead(gatt, characteristic, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    characteristic?.let {
                        val (_, characteristicUUID, dataType) = characteristicsToRead[currentCharacteristicIndex]
                        val value = when (dataType) {
                            "int" -> it.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
                            "string" -> it.getStringValue(0)
                            "hex" -> it.value.joinToString("") { byte -> "%02x".format(byte) }
                            else -> null
                        }
                        resultsMap[characteristicUUID] = value
                    }
                } else {
                    Timber.w("Failed to read characteristic: $status")
                }
                currentCharacteristicIndex++
                gatt?.let { readNextCharacteristic(it) }
            }


            @SuppressLint("MissingPermission")
            private fun readNextCharacteristic(gatt: BluetoothGatt) {
                if (currentCharacteristicIndex < characteristicsToRead.size) {
                    val (serviceUUID, characteristicUUID, _) = characteristicsToRead[currentCharacteristicIndex]
                    val service = gatt.getService(serviceUUID)
                    val char = service?.getCharacteristic(characteristicUUID)

                    if (char != null && (char.properties and BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                        gatt.readCharacteristic(char)
                    } else {
                        Timber.w("Characteristic $characteristicUUID not found or not readable.")
                        resultsMap[characteristicUUID] = null // Indicate failure for this char
                        currentCharacteristicIndex++
                        readNextCharacteristic(gatt)
                    }
                } else {
                    // Finished reading all characteristics
                    if (continuation.isActive) {
                        continuation.resume(resultsMap)
                    }
                    gatt.disconnect()
                    closeGatt()
                }
            }
        }

        // Create the GATT connection
        gatt = bluetoothDevice.connectGatt(context, false, gattCallback)

        // Handle cancellation of the coroutine
        continuation.invokeOnCancellation {
            Timber.d("Coroutine cancelled. Disconnecting and closing GATT.")
            gatt?.disconnect()
            gatt?.close()
            gatt = null
        }
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

        // Skip Samsung Find My Mobile and Apple devices if security level is set to high as this causes a lot of false positives
        if (deviceType == DeviceType.SAMSUNG_FIND_MY_MOBILE) {
            return securityLevel != "high"
        } else if (deviceType == DeviceType.APPLE) {
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
        val loggingTurnedOn: Boolean = BuildConfig.DEBUG

        var logger: FileLoggerTree = FileLoggerTree.Builder()
            .withSizeLimit(3_500_000)
            .withDir(ATTrackingDetectionApplication.getAppContext().filesDir)
            .withFileName("location.log")
            .withMinPriority(Log.VERBOSE)
            .appendToFile(true)
            .build()

        fun log(message: String) {
            if (!loggingTurnedOn) return

            Timber.d(message)
            logger.d(message)
        }
    }

    object BLELogger {
        var logger: FileLoggerTree = FileLoggerTree.Builder()
            .withSizeLimit(3_500_000)
            .withDir(ATTrackingDetectionApplication.getAppContext().filesDir)
            .withFileName("BLE_scan.log")
            .withMinPriority(Log.VERBOSE)
            .appendToFile(true)
            .build()

        fun d(message: String) {
            Timber.d(message)
            logger.d(message)
        }

        fun e(message: String) {
            Timber.e(message)
            logger.e(message)
        }

        fun wtf(message: String) {
            Timber.wtf(message)
            logger.wtf(message)
        }

        fun i(message: String) {
            Timber.i(message)
            logger.i(message)
        }

        fun v(message: String) {
            Timber.v(message)
            logger.v(message)
        }
    }
}

fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return round(this * multiplier) / multiplier
}

fun android.location.Location.privacyPrint(): String {
    if (BuildConfig.DEBUG) {
        return "(${latitude.round(3)}, ${longitude.round(3)})"
    }
    return "(${latitude.round(0)}, ${longitude.round(0)})"
}


