package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.Connectable
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import timber.log.Timber

class Tile(val id: Int) : Device(){
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_baseline_device_unknown_24

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_tile)
            .format(id)

    override val deviceContext: DeviceContext
        get() = Tile

    companion object : DeviceContext {
        // TODO: Implement scan filter for tile
        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder().setServiceUuid(offlineFindingServiceUUID).build()

        override val deviceType: DeviceType
            get() = DeviceType.TILE

        override val defaultDeviceName: String
            get() = "Tile"

        override val statusByteDeviceType: UInt
            get() = 0u

        val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FEED-0000-1000-8000-00805F9B34FB")
    }
}