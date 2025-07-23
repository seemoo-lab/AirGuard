package de.seemoo.at_tracking_detection.util.ble

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.Connectable
import timber.log.Timber

class BluetoothLeService : Service() {
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var bluetoothGatt: BluetoothGatt? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService {
            return this@BluetoothLeService
        }
    }

    fun init(): Boolean {
        val bluetoothManager =
            applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        return true
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopBLEService(bluetoothGatt)
        Timber.d("Unbinding BluetoothLeService")
        return super.onUnbind(intent)
    }

    @SuppressLint("MissingPermission")
    fun connect(baseDevice: BaseDevice): Boolean {
        if (baseDevice.device !is Connectable) {
            //TODO: Error not shown in UI!
            Timber.d("Device type is ${baseDevice.deviceType} and therefore not able to play a sound!")
            return false
        }

        // Close any existing connection before starting a new one
        if (bluetoothGatt != null) {
            Timber.d("Closing previous GATT connection before creating a new one.")
            stopBLEService(bluetoothGatt)
            bluetoothGatt = null
        }

        broadcastUpdate(BluetoothConstants.ACTION_GATT_CONNECTING)

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Timber.w("Bluetooth adapter is not initialized or not enabled!")
            return false
        }

        return try {
            val device = bluetoothAdapter?.getRemoteDevice(baseDevice.address)
            bluetoothGatt =
                device?.connectGatt(this, false, baseDevice.device.bluetoothGattCallback)
            Timber.d("New GATT connection initiated.")
            true
        } catch (e: IllegalArgumentException) {
            Timber.e("Failed to connect to device!")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBLEService(gatt: BluetoothGatt?) {
        gatt?.disconnect()
        gatt?.close()
    }

    private fun broadcastUpdate(action: String) =
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(action))
}
