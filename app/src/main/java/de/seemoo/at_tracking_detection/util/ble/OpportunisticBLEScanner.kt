package de.seemoo.at_tracking_detection.util.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.getSystemService
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.detection.LocationProvider
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.util.DefaultBuildVersionProvider
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.Util
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class OpportunisticBLEScanner(var notificationService: NotificationService?) {

    private var locationProvider: LocationProvider? = null

    init {
        if (notificationService == null) {
            notificationService =
                ATTrackingDetectionApplication.getCurrentApp()?.notificationService
        }

        val context = ATTrackingDetectionApplication.getAppContext()
        val locationManager = context.getSystemService<LocationManager>()
        if (locationManager != null) {
            locationProvider = LocationProvider(locationManager, DefaultBuildVersionProvider())
        }
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var location: Location? = null
    private var isUpdatingLocation = false

    fun startScan() {
        val scanSettings = scanSettings() ?: return

        val applicationContext = ATTrackingDetectionApplication.getAppContext()

        if (!Util.checkBluetoothPermission()) {
            Timber.d("Permission to perform bluetooth scan missing")
            return
        }
        try {
            val bluetoothManager =
                applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            val scanFilter = DeviceManager.scanFilter
            bluetoothAdapter.bluetoothLeScanner.startScan(scanFilter, scanSettings, leScanCallback)
            this.bluetoothAdapter = bluetoothAdapter

            Timber.d("Starting an opportunistic BLE Scanner. Not stopping it :D ")
        } catch (e: Throwable) {
            Timber.e("BluetoothAdapter not found!")
            return
        }
    }



    fun stopScan() {
        this.bluetoothAdapter?.bluetoothLeScanner?.let { BLEScanCallback.stopScanning(it) }
    }

    fun scanSettings(): ScanSettings? {
        if (Build.VERSION.SDK_INT >= 23) {
            if (Build.VERSION.SDK_INT >= 26) {
                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC)
                    .setLegacy(true)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setReportDelay(0)
                    .build()
                return scanSettings
            }

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
//                .setReportDelay(0)
                .build()
            return scanSettings
        }

        return null
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scanResult: ScanResult) {
            super.onScanResult(callbackType, scanResult)
            //Checks if the device has been found already
            if (SharedPrefs.isScanningInBackground) {
                Timber.d("Scan received during background scan")
            }else {
                Timber.d("Scan outside of background scan ${scanResult}")
                scanResult.timestampNanos
                val milisecondsSinceEvent = (SystemClock.elapsedRealtimeNanos() - scanResult.timestampNanos) / 1000000L
                val timeOfEvent = System.currentTimeMillis() - milisecondsSinceEvent
                val eventDate = Instant.ofEpochMilli(timeOfEvent).atZone(ZoneId.systemDefault()).toLocalDateTime()
                Timber.d("Scan received at ${eventDate.toString()}")
                if (BuildConfig.DEBUG) {
                    notificationService?.sendDebugNotificationFoundDevice(scanResult)
                }

                updateLocation()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Timber.e("Bluetooth scan failed $errorCode")
            if (BuildConfig.DEBUG) {
                notificationService?.sendBLEErrorNotification()
            }
        }
    }

    fun updateLocation() {
        if (isUpdatingLocation) {return}

        val useLocation = SharedPrefs.useLocationInTrackingDetection
        if (useLocation) {
            val lastLocation = locationProvider?.getLastLocation(false)

            if (lastLocation != null) {
                val millisecondsSinceLocation = (SystemClock.elapsedRealtimeNanos() - lastLocation.elapsedRealtimeNanos) / 1000000L
                val timeOfLocationevent = System.currentTimeMillis() - millisecondsSinceLocation
                val locationDate = Instant.ofEpochMilli(timeOfLocationevent).atZone(ZoneId.systemDefault()).toLocalDateTime()
                val timeDiff = ChronoUnit.SECONDS.between(locationDate, LocalDateTime.now())

                if (timeDiff <= LocationProvider.MAX_AGE_SECONDS && lastLocation.accuracy <= LocationProvider.MIN_ACCURACY_METER) {
                    location = lastLocation
                    Timber.d("Using last location for the tag detection")
                }else {
                    Timber.d("Last location is too old. ${timeDiff}min")
                   fetchCurrentLocation()
                }
            }else {
                Timber.d("No last location found")
                fetchCurrentLocation()
            }
        }
    }

    fun fetchCurrentLocation() {
        //Getting the most accurate location here
        isUpdatingLocation = true
        locationProvider?.getCurrentLocation { loc ->
            location = loc
            Timber.d("Updated to current location")
            isUpdatingLocation = false
        }
    }
}