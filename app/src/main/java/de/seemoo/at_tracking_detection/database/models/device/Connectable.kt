package de.seemoo.at_tracking_detection.database.models.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication

interface Connectable {
    val bluetoothGattCallback: BluetoothGattCallback

    fun broadcastUpdate(action: String) =
        LocalBroadcastManager.getInstance(ATTrackingDetectionApplication.getAppContext())
            .sendBroadcast(Intent(action))

    @SuppressLint("MissingPermission")
    fun disconnect(gatt: BluetoothGatt?) {
        gatt?.disconnect()
        gatt?.close()
    }
}