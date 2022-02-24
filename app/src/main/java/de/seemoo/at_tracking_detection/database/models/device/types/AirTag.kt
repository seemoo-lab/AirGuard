package de.seemoo.at_tracking_detection.database.models.device.types

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanFilter
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

class AirTag(val id: Int) : Device(), Connectable {

    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_airtag

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_airtag)
            .format(id)

    override val deviceContext: DeviceContext
        get() = AirTag

    override val bluetoothGattCallback: BluetoothGattCallback
        get() = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            // Connect permission is checked before this function is called
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                            BluetoothProfile.STATE_DISCONNECTED -> broadcastUpdate(
                                BluetoothConstants.ACTION_GATT_DISCONNECTED
                            )
                            else -> {
                                Timber.d("Connection state changed to $newState")
                            }
                        }
                    }
                    19 -> {
                        broadcastUpdate(BluetoothConstants.ACTION_EVENT_COMPLETED)
                    }
                    else -> broadcastUpdate(BluetoothConstants.ACTION_EVENT_FAILED)
                }
            }

            @SuppressLint("MissingPermission")
            // Connect permission is checked before this function is called
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(AIR_TAG_SOUND_SERVICE)
                if (service == null) {
                    Timber.e("AirTag sound service not found!")
                    disconnect(gatt)
                    broadcastUpdate(BluetoothConstants.ACTION_EVENT_FAILED)
                } else {
                    service.getCharacteristic(AIR_TAG_SOUND_CHARACTERISTIC)
                        .let {
                            it.setValue(175, BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                            gatt.writeCharacteristic(it)
                            broadcastUpdate(BluetoothConstants.ACTION_EVENT_RUNNING)
                            Timber.d("Playing sound...")
                        }
                }
                super.onServicesDiscovered(gatt, status)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                    when (characteristic.properties and AIR_TAG_EVENT_CALLBACK) {
                        AIR_TAG_EVENT_CALLBACK -> {
                            broadcastUpdate(
                                BluetoothConstants.ACTION_EVENT_COMPLETED
                            )
                            disconnect(gatt)
                        }
                    }
                }
                else if (status == 133) { // GATT_ERROR, Timeout
                    broadcastUpdate(BluetoothConstants.ACTION_EVENT_FAILED)
                    disconnect(gatt)
                }else {
                    disconnect(gatt)
                }
                super.onCharacteristicWrite(gatt, characteristic, status)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                    when (characteristic.properties and AIR_TAG_EVENT_CALLBACK) {
                        AIR_TAG_EVENT_CALLBACK -> {
                            broadcastUpdate(
                                BluetoothConstants.ACTION_EVENT_COMPLETED
                            )
                            disconnect(gatt)
                        }
                    }
                }
            }
        }

    companion object : DeviceContext {
        private val AIR_TAG_SOUND_SERVICE = UUID.fromString("7DFC9000-7D1C-4951-86AA-8D9728F8D66C")
        private val AIR_TAG_SOUND_CHARACTERISTIC =
            UUID.fromString("7DFC9001-7D1C-4951-86AA-8D9728F8D66C")
        private const val AIR_TAG_EVENT_CALLBACK = 0x302

        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setManufacturerData(
                    0x4C,
                    byteArrayOf((0x12).toByte(), (0x19).toByte(), (0x10).toByte()),
                    byteArrayOf((0xFF).toByte(), (0xFF).toByte(), (0x18).toByte())
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.AIRTAG

        override val defaultDeviceName: String
            get() = "AirTag"

        override val statusByteDeviceType: UInt
            get() = 1u
    }
}