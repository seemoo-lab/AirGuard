package de.seemoo.at_tracking_detection.util.ble

import android.Manifest
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.detection.LocationRequester
import timber.log.Timber


/***
 * BLE Scanner to be used for foreground scans when the app is opened
 * Not to be used for Background scanning. This is handled in the `ScanBluetoothWorker`
 */
object BLEScanner {
    var callbacks = ArrayList<ScanCallback>()
    var isScanning = false
    private var lastLocation: Location? = null

    // Contains the last 10 scan results
    private var scanResults = ArrayList<ScanResult>()

    fun startBluetoothScan(appContext: Context): Boolean {
        if (!hasScanPermission()) {
            Timber.d("Missing BLUETOOTH_SCAN permission.")
            return false
        }
        val scanner = ScanOrchestrator.getLeScanner()
        if (scanner == null) {
            Timber.d("BluetoothLeScanner is null (adapter off or transient).")
            return false
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanResults.clear()
        val scanFilter = DeviceManager.scanFilter

        ScanOrchestrator.startScan(
            callerTag = "BLEScanner",
            filters = scanFilter,
            settings = scanSettings,
            callback = ownScanCallback,
            allowReplaceExisting = true,
            priority = ScanOrchestrator.Priority.HIGH
        )
        isScanning = true
        fetchCurrentLocation()
        Timber.d("Bluetooth foreground scan requested")
        return true
    }

    fun stopBluetoothScan() {
        callbacks.clear()
        ScanOrchestrator.stopScan("BLEScanner", ownScanCallback)
        isScanning = false
        scanResults.clear()
        Timber.d("Bluetooth scan stopped (requested)")
    }

    fun registerCallback(callback: ScanCallback) {
        callbacks.add(callback)
        Timber.d("New BLE ScanCallback registered")
        scanResults.forEach {
            callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it)
        }
    }

    fun unregisterCallback(callback: ScanCallback) {
        callbacks.remove(callback)
    }

    private var ownScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let { scanResult ->
                scanResults.add(0, scanResult)
                if (scanResults.size > 10) scanResults.removeLastOrNull()
                callbacks.forEach { it.onScanResult(callbackType, result) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Timber.e("BLE Scan failed. $errorCode")
            isScanning = false
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            callbacks.forEach { it.onBatchScanResults(results) }
        }
    }

    private fun fetchCurrentLocation() {
        val locationProvider = ATTrackingDetectionApplication.getCurrentApp().locationProvider
        val loc =
            locationProvider.lastKnownOrRequestLocationUpdates(locationRequester, timeoutMillis = null)
        if (loc != null) {
            this.lastLocation = loc
        }
    }

    private var locationRequester = object : LocationRequester() {
        override fun receivedAccurateLocationUpdate(location: Location) {
            this@BLEScanner.lastLocation = location
        }
    }

    // Consider Bluetooth "available" for scanning when scanner exists and permission granted
    fun isBluetoothOn(): Boolean {
        if (!hasScanPermission()) return false
        return ScanOrchestrator.getLeScanner() != null
    }

    fun openBluetoothSettings(context: Context) {
        val intentOpenBluetoothSettings = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        context.startActivity(intentOpenBluetoothSettings)
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                ATTrackingDetectionApplication.getAppContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }
}
