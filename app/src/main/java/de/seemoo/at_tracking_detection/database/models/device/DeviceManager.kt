package de.seemoo.at_tracking_detection.database.models.device

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.IntentFilter
import de.seemoo.at_tracking_detection.database.models.device.types.*
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants

object DeviceManager {

    val devices = listOf(AirTag, FindMy, AirPods, AppleDevice)

    fun getDeviceType(scanResult: ScanResult): DeviceType {
        val manufacturerData = scanResult.scanRecord?.getManufacturerSpecificData(0x004c) ?: return Unknown.deviceType
        val statusByte: UInt = manufacturerData[3].toUInt()
        // Get the correct int from the byte
        val deviceTypeInt =  (statusByte.toInt() and 0x30 shr 4).toUInt()

        for (device in devices) {
            // Implementation of device detection is incorrect.
//            if (device.bluetoothFilter.matches(scanResult)) {
//                return device.deviceType
//            }
            if (device.statusByteDeviceType == deviceTypeInt) {
                return device.deviceType
            }

        }
        return Unknown.deviceType
    }

    val scanFilter: List<ScanFilter> = devices.map { it.bluetoothFilter }

    val gattIntentFilter: IntentFilter = IntentFilter().apply {
        addAction(BluetoothConstants.ACTION_EVENT_RUNNING)
        addAction(BluetoothConstants.ACTION_GATT_DISCONNECTED)
        addAction(BluetoothConstants.ACTION_EVENT_COMPLETED)
        addAction(BluetoothConstants.ACTION_EVENT_FAILED)
    }
}