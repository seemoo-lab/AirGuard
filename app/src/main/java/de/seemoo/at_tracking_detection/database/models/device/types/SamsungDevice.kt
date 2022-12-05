package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType

open class SamsungDevice(open val id: Int) : Device(){
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_smarttag_icon

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_samsung_device)
            .format(id)

    override val deviceContext: DeviceContext
        get() = SamsungDevice

    companion object : DeviceContext {
        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setServiceData(
                    offlineFindingServiceUUID,
                    // 13, FF --> After 24 Hours, 12, FE --> After 15 Minutes
                    byteArrayOf((0x13).toByte()),
                    byteArrayOf((0xFF).toByte())
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.SAMSUNG

        override val defaultDeviceName: String
            get() = "Samsung Device"

        override val statusByteDeviceType: UInt
            get() = 0u

        private val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")
    }

}