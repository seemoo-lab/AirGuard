package de.seemoo.at_tracking_detection.util.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.location.Location
import android.provider.Settings
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetwork
import de.seemoo.at_tracking_detection.detection.LocationRequester
import de.seemoo.at_tracking_detection.util.Utility
import timber.log.Timber


/***
 * BLE Scanner to be used for foreground scans when the app is opened
 * Not to be used for Background scanning. This is handled in the `ScanBluetoothWorker`
 */
object BLEScanner {
    private var bluetoothManager: BluetoothManager? = null
    var callbacks = ArrayList<ScanCallback>()
    var isScanning = false
    private var lastLocation: Location? = null

    // Contains the last 10 scan results
    private var scanResults = ArrayList<ScanResult>()

    fun startBluetoothScan(appContext: Context): Boolean {
        // Check if already scanning
        if(this.bluetoothManager != null && isScanning) { return true }

        this.bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = this.bluetoothManager?.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Timber.d("Bluetooth is not enabled.")
            return false
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        scanResults.clear()

        this.bluetoothManager?.let {
            val isBluetoothEnabled = it.adapter.state == BluetoothAdapter.STATE_ON
            val hasScanPermission = Utility.checkBluetoothPermission()

            if (isBluetoothEnabled && hasScanPermission) {
                val leScanner = it.adapter.bluetoothLeScanner
                val scanFilter = DeviceManager.scanFilter
                leScanner.startScan(scanFilter, scanSettings, ownScanCallback)
                isScanning = true
                fetchCurrentLocation()
                Timber.d("Bluetooth foreground scan started")
                return true
            }

            return false
        }
        return false
    }


    fun stopBluetoothScan() {
        callbacks.clear()
        bluetoothManager?.let {
            if (it.adapter.state == BluetoothAdapter.STATE_ON) {
                if (!Utility.checkBluetoothPermission()) {return}

                it.adapter.bluetoothLeScanner.stopScan(ownScanCallback)
            }
        }
        isScanning = false
        scanResults.clear()
        Timber.d("Bluetooth scan stopped")
    }

    fun registerCallback(callback: ScanCallback) {
        callbacks.add(callback)
        Timber.d("New BLE ScanCallback registered")

        //Pass the last results
        scanResults.forEach {
            callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it)
        }
    }

    fun unregisterCallback(callback: ScanCallback) {
        callbacks.remove(callback)
        // Timber.d("BLE ScanCallback unregistered")
    }

    private var ownScanCallback = object: ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            // TODO: Add scan result to DB here. Detection events should not be to close after each other.
            // New detection events (Beacons) every 15min
            // Timber.d("Found a device $result")
            result?.let { scanResult ->
                scanResults.add(0, scanResult)
                if (scanResults.size > 10) {
                    scanResults.removeLastOrNull()
                }
            }

            callbacks.forEach {
                it.onScanResult(callbackType, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            callbacks.forEach {
                it.onScanFailed(errorCode)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            // Batch results are only delivered when using low power scanning.
            // We use low latency so this method should not be called.
            // We implement it anyways to be save.

            //TODO: Add scan result to DB here
            callbacks.forEach {
                it.onBatchScanResults(results)
            }
        }
    }

    private fun fetchCurrentLocation() {
        // We fetch the current location and cache for saving the results to the DB
        val locationProvider = ATTrackingDetectionApplication.getCurrentApp().locationProvider
        val loc =
            locationProvider.lastKnownOrRequestLocationUpdates(locationRequester, timeoutMillis = null)
        if (loc != null) {
            this.lastLocation = loc
        }
    }

    private var locationRequester = object: LocationRequester() {
        override fun receivedAccurateLocationUpdate(location: Location) {
            this@BLEScanner.lastLocation = location
        }
    }

    fun isBluetoothOn(): Boolean {
        val adapter = bluetoothManager?.adapter
        return adapter != null && adapter.isEnabled
    }

    fun openBluetoothSettings(context: Context) {
        val intentOpenBluetoothSettings = Intent()
        intentOpenBluetoothSettings.action = Settings.ACTION_BLUETOOTH_SETTINGS
        context.startActivity(intentOpenBluetoothSettings)
    }
}