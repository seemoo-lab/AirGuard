package de.seemoo.at_tracking_detection.database.models.device

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.IntentFilter
import de.seemoo.at_tracking_detection.database.models.device.types.AirTag
import de.seemoo.at_tracking_detection.database.models.device.types.Unknown
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants

object DeviceManager {

    private val devices = listOf(AirTag)

    fun getDeviceType(scanResult: ScanResult): DeviceType {
        for (device in devices) {
            if (device.bluetoothFilter.matches(scanResult)) {
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