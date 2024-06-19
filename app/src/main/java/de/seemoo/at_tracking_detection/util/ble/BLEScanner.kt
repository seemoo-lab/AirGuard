package de.seemoo.at_tracking_detection.util.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.location.Location
import android.provider.Settings
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.detection.LocationRequester
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.Utility
import timber.log.Timber
import java.util.LinkedList
import java.util.Queue
import java.util.UUID


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

    private val GATT_GENERIC_ACCESS_UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
    private val GATT_DEVICE_NAME_UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
    private val GATT_APPEARANCE_UUID = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb")

    private val GATT_DEVICE_INFORMATION_UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
    private val GATT_MANUFACTURER_NAME_UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")

    private val connectedDevices = mutableSetOf<String>()

    val deviceNames = mutableMapOf<String, String>()
    val appearances = mutableMapOf<String, Int>()
    val manufacturers = mutableMapOf<String, String>()

    private var pendingScanResults = mutableMapOf<String, ScanResultWrapper>()

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
            result?.let { scanResult ->
                if (scanResult.device.address !in pendingScanResults) {
                    val scanResultWrapper = ScanResultWrapper(scanResult)
                    pendingScanResults[scanResult.device.address] = scanResultWrapper
                    connectToDevice(scanResult.device)
                } else {
                    // Device already in the process, ignore this scan result for now
                }
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
            callbacks.forEach {
                it.onBatchScanResults(results)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (device.address in connectedDevices) {
            Timber.d("Already connected to device: ${device.address}. Skipping connection.")
            return
        }
        Timber.d("Connecting to device: ${device.address}")
        device.connectGatt(null, false, gattCallback)
        connectedDevices.add(device.address)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        private val characteristicQueue: Queue<BluetoothGattCharacteristic> = LinkedList()

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Timber.d("Connected to GATT server. Attempting to start service discovery: ${gatt.discoverServices()}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Timber.d("Disconnected from GATT server.")
                gatt.close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val genericAccessService = gatt.getService(GATT_GENERIC_ACCESS_UUID)
                val deviceInfoService = gatt.getService(GATT_DEVICE_INFORMATION_UUID)

                characteristicQueue.clear()

                genericAccessService?.let {
                    it.getCharacteristic(GATT_DEVICE_NAME_UUID)?.let { characteristicQueue.add(it) }
                    it.getCharacteristic(GATT_APPEARANCE_UUID)?.let { characteristicQueue.add(it) }
                }

                deviceInfoService?.let {
                    it.getCharacteristic(GATT_MANUFACTURER_NAME_UUID)?.let { characteristicQueue.add(it) }
                }

                if (characteristicQueue.isNotEmpty()) {
                    gatt.readCharacteristic(characteristicQueue.poll())
                }
            } else {
                Timber.d("onServicesDiscovered received: $status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            val address = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    GATT_DEVICE_NAME_UUID -> {
                        val deviceName = characteristic.getStringValue(0)
                        Timber.d("Retrieve device name: $deviceName")
                        deviceNames[address] = deviceName
                    }
                    GATT_APPEARANCE_UUID -> {
                        val appearance = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0)
                        Timber.d("Retrieve appearance: $appearance")
                        appearances[address] = appearance
                    }
                    GATT_MANUFACTURER_NAME_UUID -> {
                        val manufacturerName = characteristic.getStringValue(0)
                        Timber.d("Retrieve manufacturer name: $manufacturerName")
                        manufacturers[address] = manufacturerName
                    }
                }

                if (characteristicQueue.isNotEmpty()) {
                    gatt.readCharacteristic(characteristicQueue.poll())
                } else {
                    gatt.disconnect()
                    pendingScanResults[address]?.isInfoComplete = true
                    notifyCallbacks(pendingScanResults[address])
                }
            } else {
                Timber.d("Failed to read characteristic: ${characteristic.uuid}, status: $status")
                gatt.disconnect()
                pendingScanResults[address]?.isInfoComplete = true
                notifyCallbacks(pendingScanResults[address])
            }
        }
    }

    private fun notifyCallbacks(scanResultWrapper: ScanResultWrapper?) {
        scanResultWrapper?.let { wrapper ->
            callbacks.forEach {
                it.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, wrapper.scanResult)
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