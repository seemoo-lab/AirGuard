package de.seemoo.at_tracking_detection.database.tables.device

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanResult
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.util.DeviceType
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import timber.log.Timber
import java.util.*

class AirTag(scanResult: ScanResult) : Device(scanResult) {
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_airtag

    override val deviceType: Int
        get() = DeviceType.AIRTAG

    override val deviceName: String
        get() = "AirTag"

    override val deviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_airtag)
            .format(deviceId)

    override val bluetoothGattCallback: BluetoothGattCallback
        get() = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                Timber.d("Connected to gatt device!")
                                gatt.discoverServices()
                                broadcastUpdate(BluetoothConstants.ACTION_GATT_CONNECTED)
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
                    19 -> {
                        broadcastUpdate(BluetoothConstants.ACTION_EVENT_COMPLETED)
                    }
                    else -> {
                        Timber.e("Failed to connect to bluetooth device! Status: $status")
                        broadcastUpdate(BluetoothConstants.ACTION_EVENT_FAILED)
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(AIR_TAG_SOUND_SERVICE)
                if (service == null) {
                    Timber.e("Airtag sound service not found!")
                } else {
                    service.getCharacteristic(AIR_TAG_SOUND_CHARACTERISTIC)
                        .let {
                            it.setValue(175, BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                            gatt.writeCharacteristic(it)
                            Timber.d("Playing sound...")
                        }
                }
                super.onServicesDiscovered(gatt, status)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                    when (characteristic.properties and AIR_TAG_EVENT_CALLBACK) {
                        AIR_TAG_EVENT_CALLBACK -> broadcastUpdate(
                            BluetoothConstants.ACTION_EVENT_COMPLETED
                        )
                    }
                }
            }
        }

    companion object {
        private val AIR_TAG_SOUND_SERVICE = UUID.fromString("7DFC9000-7D1C-4951-86AA-8D9728F8D66C")
        private val AIR_TAG_SOUND_CHARACTERISTIC =
            UUID.fromString("7DFC9001-7D1C-4951-86AA-8D9728F8D66C")
        private const val AIR_TAG_EVENT_CALLBACK = 0x302
    }
}