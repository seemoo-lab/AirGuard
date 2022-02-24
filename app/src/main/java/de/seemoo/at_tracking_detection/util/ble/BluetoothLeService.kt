package de.seemoo.at_tracking_detection.util.ble

import android.app.Service
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.Connectable
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants.ACTION_EVENT_COMPLETED
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants.ACTION_EVENT_FAILED
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants.ACTION_GATT_DISCONNECTED
import timber.log.Timber
import java.util.*

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

    fun connect(baseDevice: BaseDevice): Boolean {
        if (baseDevice.device !is Connectable) {
            //TODO: Error not shown in UI!
            Timber.d("Device type is ${baseDevice.deviceType} and therefore not able to play a sound!")
            return false
        }
        broadcastUpdate(BluetoothConstants.ACTION_GATT_CONNECTING)
        bluetoothAdapter?.let {
            return try {
                val device = it.getRemoteDevice(baseDevice.address)
                bluetoothGatt =
                    device.connectGatt(this, false, baseDevice.device.bluetoothGattCallback)
                true
            } catch (e: IllegalArgumentException) {
                Timber.e("Failed to connect to device!")
                false
            }
        } ?: run {
            Timber.w("Bluetooth adapter is not initialized!")
            return false
        }
    }

    fun stopBLEService(gatt: BluetoothGatt?) {
        gatt?.disconnect()
        gatt?.close()
//        stopSelf()
    }


    private val bluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                GATT_SUCCESS -> {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Timber.d("Connected to gatt device!")
                            gatt.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            broadcastUpdate(ACTION_GATT_DISCONNECTED)
                            Timber.d("Disconnected from gatt device!")
                        }
                        else -> {
                            Timber.d("Connection state changed to $status")
                        }
                    }
                }
                19 -> {
                    broadcastUpdate(ACTION_EVENT_COMPLETED)
                }
                else -> {
                    Timber.e("Failed to connect to bluetooth device! Status: $status")
                    broadcastUpdate(ACTION_EVENT_FAILED)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            playSoundOnAirTag(gatt)
            super.onServicesDiscovered(gatt, status)
        }

        fun playSoundOnAirTag(gatt: BluetoothGatt) {
            val service = gatt.getService(AIR_TAG_SOUND_SERVICE)
            if (service == null) {
                playSoundOnFindMyDevice(gatt)
                return
            }
            service.getCharacteristic(AIR_TAG_SOUND_CHARACTERISTIC)
                .let {
                    it.setValue(AIR_TAG_START_SOUND_OPCODE, FORMAT_UINT8, 0)
                    gatt.writeCharacteristic(it)
                    Timber.d("Playing sound on AirTag...")
                }
            broadcastUpdate(BluetoothConstants.ACTION_EVENT_RUNNING)
        }

        fun playSoundOnFindMyDevice(gatt: BluetoothGatt) {
            val uuids = gatt.services.map { it.uuid.toString() }
            Timber.d("Found UUIDS $uuids")
            val service = gatt.services.firstOrNull {
                it.uuid.toString().lowercase().contains(
                    FINDMY_SOUND_SERVICE.lowercase()
                )
            }

            if (service == null) {
                Timber.e("Playing sound service not found!")
                return
            }

            val uuid = FINDMY_SOUND_CHARACTERISTIC
            val characteristic = service.getCharacteristic(uuid)
            characteristic.let {
                gatt.setCharacteristicNotification(it, true)
                it.value = FINDMY_START_SOUND_OPCODE
                gatt.writeCharacteristic(it)
                Timber.d("Playing sound on Find My device with ${it.uuid}")
            }
            broadcastUpdate(BluetoothConstants.ACTION_EVENT_RUNNING)
        }

        fun stopSoundOnFindMyDevice(gatt: BluetoothGatt) {
            val service = gatt.services.firstOrNull {
                it.uuid.toString().lowercase().contains(
                    FINDMY_SOUND_SERVICE
                )
            }

            if (service == null) {
                Timber.d("Sound service not found")
                return
            }

            val uuid = FINDMY_SOUND_CHARACTERISTIC
            val characteristic = service.getCharacteristic(uuid)
            characteristic.let {
                gatt.setCharacteristicNotification(it, true)
                it.value = FINDMY_STOP_SOUND_OPCODE
                gatt.writeCharacteristic(it)
                Timber.d("Stopping sound on Find My device with ${it.uuid}")

            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == GATT_SUCCESS && characteristic != null) {
                when (characteristic.properties and AIR_TAG_EVENT_CALLBACK) {
                    AIR_TAG_EVENT_CALLBACK -> broadcastUpdate(ACTION_EVENT_COMPLETED)
                }
            }
            Timber.d("Read characteristic ${characteristic?.value}")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == GATT_SUCCESS) {
                Timber.d("Finished writing to characteristic")
                if (characteristic?.value.contentEquals(FINDMY_START_SOUND_OPCODE) && gatt != null) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        stopSoundOnFindMyDevice(gatt)
                    }, 5000)
                }

                if (characteristic?.value.contentEquals(FINDMY_STOP_SOUND_OPCODE)) {
                    stopBLEService(gatt)
                    broadcastUpdate(ACTION_EVENT_COMPLETED)
                }

                if (characteristic?.uuid == AIR_TAG_SOUND_CHARACTERISTIC) {
                    stopBLEService(gatt)
                    broadcastUpdate(ACTION_EVENT_COMPLETED)
                }

            } else {
                Timber.d("Writing to characteristic failed ${characteristic?.uuid}")
                stopBLEService(gatt)
            }

            super.onCharacteristicWrite(gatt, characteristic, status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.let {
                Timber.d("Characteristic changed ${it.uuid.toString()}")
                Timber.d("Value: ${it.value}")
            }
            super.onCharacteristicChanged(gatt, characteristic)
        }
    }

    private fun broadcastUpdate(action: String) =
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(action))

    companion object {
        private val AIR_TAG_SOUND_SERVICE = UUID.fromString("7DFC9000-7D1C-4951-86AA-8D9728F8D66C")
        private val AIR_TAG_SOUND_CHARACTERISTIC =
            UUID.fromString("7DFC9001-7D1C-4951-86AA-8D9728F8D66C")
        private const val AIR_TAG_START_SOUND_OPCODE = 175
        private const val AIR_TAG_EVENT_CALLBACK = 0x302

        private const val FINDMY_SOUND_SERVICE = "fd44"
        private val FINDMY_SOUND_CHARACTERISTIC =
            UUID.fromString("4F860003-943B-49EF-BED4-2F730304427A")
        internal val FINDMY_START_SOUND_OPCODE = byteArrayOf(0x01, 0x00, 0x03)
        private val FINDMY_STOP_SOUND_OPCODE = byteArrayOf(0x01, 0x01, 0x03)
        private var writtenValue = 0
    }
}
