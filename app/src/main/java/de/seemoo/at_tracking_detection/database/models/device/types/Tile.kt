package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
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

class Tile(val id: Int) : Device(), Connectable {
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_baseline_device_unknown_24

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_tile)
            .format(id)

    override val deviceContext: DeviceContext
        get() = AirPods

    override val bluetoothGattCallback: BluetoothGattCallback
        get() = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        when (newState) {
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

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {

            }
        }

    companion object : DeviceContext {
        // TODO: Implement scan filter for tile
        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder().setDeviceAddress("FF:FF:FF:FF:FF:FF").build()

        override val deviceType: DeviceType
            get() = DeviceType.TILE

        override val defaultDeviceName: String
            get() = "Tile"

        override val statusByteDeviceType: UInt
            get() = 0u
    }
}