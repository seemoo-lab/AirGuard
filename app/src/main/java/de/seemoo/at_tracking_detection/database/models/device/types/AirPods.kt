package de.seemoo.at_tracking_detection.database.models.device.types

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanFilter
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.Connectable
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import timber.log.Timber
import java.util.*

class AirPods(val id: Int) : Device(), Connectable {

    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_airpods

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_airpods)
            .format(id)

    override val deviceContext: DeviceContext
        get() = AirPods

    override val bluetoothGattCallback: BluetoothGattCallback
        get() = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                Timber.d("Connected to gatt device!")
                                gatt.discoverServices()
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                broadcastUpdate(BluetoothConstants.ACTION_GATT_DISCONNECTED)
                                Timber.d("Disconnected from gatt device!")
                            }
                            else -> {
                                Timber.d("Connection state changed to $newState")
                            }
                        }
                    }
                    else -> {
                        Timber.e("Failed to connect to bluetooth device! Status: $status")
                        broadcastUpdate(BluetoothConstants.ACTION_EVENT_FAILED)
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val uuids = gatt.services.map { it.uuid.toString() }
                Timber.d("Found UUIDS $uuids")
                val service = gatt.services.firstOrNull {
                    it.uuid.toString().lowercase().contains(
                        AIRPODS_SOUND_SERVICE.lowercase()
                    )
                }

                if (service == null) {
                    Timber.e("Playing sound service not found!")
                    disconnect(gatt)
                    broadcastUpdate(BluetoothConstants.ACTION_EVENT_FAILED)
                    return
                }

                val characteristic = service.getCharacteristic(AIRPODS_SOUND_CHARACTERISTIC)
                characteristic.let {
                    gatt.setCharacteristicNotification(it, true)
                    it.value = AIRPODS_START_SOUND_OPCODE
                    gatt.writeCharacteristic(it)
                    Timber.d("Playing sound on Find My device with ${it.uuid}")
                    broadcastUpdate(BluetoothConstants.ACTION_EVENT_RUNNING)
                }
            }


            @SuppressLint("MissingPermission")
            fun stopSoundOnAirPods(gatt: BluetoothGatt) {
                val service = gatt.services.firstOrNull {
                    it.uuid.toString().lowercase().contains(
                       AIRPODS_SOUND_SERVICE
                    )
                }

                if (service == null) {
                    Timber.d("Sound service not found")
                    return
                }

                val uuid = AIRPODS_SOUND_CHARACTERISTIC
                val characteristic = service.getCharacteristic(uuid)
                characteristic.let {
                    gatt.setCharacteristicNotification(it, true)
                    it.value = AIRPODS_STOP_SOUND_OPCODE
                    gatt.writeCharacteristic(it)
                    Timber.d("Stopping sound on Find My device with ${it.uuid}")

                }
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Timber.d("Finished writing to characteristic")
                    if (characteristic?.value.contentEquals(AIRPODS_START_SOUND_OPCODE) && gatt != null) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            stopSoundOnAirPods(gatt)
                        }, 5000)
                    }

                    if (characteristic?.value.contentEquals(AIRPODS_STOP_SOUND_OPCODE)) {
                        disconnect(gatt)
                        broadcastUpdate(BluetoothConstants.ACTION_EVENT_COMPLETED)
                    }

                } else {
                    Timber.d("Writing to characteristic failed ${characteristic?.uuid}")
                    disconnect(gatt)
                    broadcastUpdate(BluetoothConstants.ACTION_EVENT_FAILED)
                }
                super.onCharacteristicWrite(gatt, characteristic, status)
            }
        }

    companion object : DeviceContext {
        internal const val AIRPODS_SOUND_SERVICE = "fd44"
        internal val AIRPODS_SOUND_CHARACTERISTIC =
            UUID.fromString("4F860003-943B-49EF-BED4-2F730304427A")
        internal val AIRPODS_START_SOUND_OPCODE = byteArrayOf(0x01, 0x00, 0x03)
        internal val AIRPODS_STOP_SOUND_OPCODE = byteArrayOf(0x01, 0x01, 0x03)

        // What does this scan filter do?
        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setManufacturerData(
                    0x4C,
                    byteArrayOf((0x12).toByte(), (0x19).toByte(), (0x18).toByte()), // Empty status byte?
                    byteArrayOf((0xFF).toByte(), (0xFF).toByte(), (0x18).toByte()) // ff?
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.AIRPODS

        override val defaultDeviceName: String
            get() = "AirPods"

        override val statusByteDeviceType: UInt
            get() = 3u
    }
}