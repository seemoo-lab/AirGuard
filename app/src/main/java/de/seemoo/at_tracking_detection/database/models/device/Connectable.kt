package de.seemoo.at_tracking_detection.database.models.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.util.ble.BluetoothEvent

interface Connectable {
    val bluetoothGattCallback: BluetoothGattCallback

    fun sendBluetoothEvent(event: BluetoothEvent) {
        val eventManager = ATTrackingDetectionApplication.getCurrentApp()?.bluetoothEventManager
            ?: error("ATTrackingDetectionApplication not initialized")
        eventManager.trySendEvent(event)
    }

    @SuppressLint("MissingPermission")
    fun disconnect(gatt: BluetoothGatt?) {
        gatt?.disconnect()
        gatt?.close()
    }
}