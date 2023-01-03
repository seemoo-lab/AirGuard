package de.seemoo.at_tracking_detection.database.models.device.types

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.*
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import timber.log.Timber
import java.util.*

class Tile(val id: Int) : Device(){
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_tile

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_tile)
            .format(id)

    override val deviceContext: DeviceContext
        get() = Tile

    companion object : DeviceContext {
        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setServiceData(
                    offlineFindingServiceUUID,
                    byteArrayOf((0x02).toByte(), (0x00).toByte()),
                    byteArrayOf((0xFF).toByte(), (0xFF).toByte())
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.TILE

        override val defaultDeviceName: String
            get() = "Tile"

        override val statusByteDeviceType: UInt
            get() = 0u

        val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FEED-0000-1000-8000-00805F9B34FB")
    }
}