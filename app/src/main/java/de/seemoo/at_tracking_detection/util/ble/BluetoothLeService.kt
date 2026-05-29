package de.seemoo.at_tracking_detection.util.ble

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.Connectable
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothLeService : Service() {
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var bluetoothGatt: BluetoothGatt? = null

    private val binder = LocalBinder()

    @Inject
    lateinit var bluetoothEventManager: BluetoothEventManager

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
        Timber.d("BluetoothLeService unbound — stopping BLE service")
        stopBLEService(bluetoothGatt)
        bluetoothGatt = null
        return super.onUnbind(intent)
    }

    @SuppressLint("MissingPermission")
    fun connect(baseDevice: BaseDevice): Boolean {
        if (baseDevice.device !is Connectable) {
            Timber.d("Device type ${baseDevice.deviceType} is not Connectable — cannot play sound")
            return false
        }

        // Close any existing connection before starting a new one
        if (bluetoothGatt != null) {
            Timber.d("Closing previous GATT connection before creating a new one")
            stopBLEService(bluetoothGatt)
            bluetoothGatt = null
        }

        bluetoothEventManager.trySendEvent(BluetoothEvent.Connecting)

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Timber.w("Bluetooth adapter is not initialised or not enabled")
            return false
        }

        return try {
            val device = bluetoothAdapter?.getRemoteDevice(baseDevice.address)
            if (device == null) {
                Timber.e("Failed to get remote device for address ${baseDevice.address}")
                return false
            }
            Timber.d("Connecting to ${baseDevice.address} (type=${baseDevice.deviceType}) via TRANSPORT_LE")
            @Suppress("DEPRECATION")
            bluetoothGatt = device.connectGatt(
                this,
                false,
                baseDevice.device.bluetoothGattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
            Timber.d("GATT connection initiated for ${baseDevice.address}")
            true
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Failed to connect — invalid address ${baseDevice.address}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopSound() {
        Timber.d("stopSound() called from UI — disconnecting GATT")
        stopBLEService(bluetoothGatt)
        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    fun stopBLEService(gatt: BluetoothGatt?) {
        Timber.d("stopBLEService: disconnecting and closing gatt=$gatt")
        gatt?.disconnect()
        gatt?.close()
    }
}
