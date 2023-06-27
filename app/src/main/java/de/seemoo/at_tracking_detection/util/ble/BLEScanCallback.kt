package de.seemoo.at_tracking_detection.util.ble

import android.bluetooth.le.*
import de.seemoo.at_tracking_detection.util.Utility
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * This static class is used to handle scan callbacks without causing memory leaks. It seems like Android can cause a leak here, but since we use a static scan callback now + store the actual callback only weakly no leaks should occur.
 */
object BLEScanCallback {

    private var scanCallback: WeakReference<ScanCallback>? = null

    fun startScanning(leScanner: BluetoothLeScanner, filters: List<ScanFilter>, settings: ScanSettings, callback: ScanCallback) {
        if (!Utility.checkBluetoothPermission()) {
            Timber.e("NO BLE SCAN PERMISSION")
            return
        }

        scanCallback = WeakReference(callback)
        leScanner.startScan(filters, settings, objectScanCallback)
        Timber.d("BLE scanning started")
    }

    fun stopScanning(leScanner: BluetoothLeScanner) {
        if (!Utility.checkBluetoothPermission()) {return}

        leScanner.stopScan(objectScanCallback)
        scanCallback?.clear()
        scanCallback = null
    }

    private val objectScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            scanCallback?.get()?.onScanResult(callbackType, result)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Timber.e("BLE Scan failed. $errorCode")
            scanCallback?.get()?.onScanFailed(errorCode)
        }
    }
}