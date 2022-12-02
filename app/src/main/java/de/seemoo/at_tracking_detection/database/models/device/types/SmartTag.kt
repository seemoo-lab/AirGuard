package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType

class SmartTag(val id: Int) : Device() {
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_smarttag_icon

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_smarttag)
            .format(id)

    override val deviceContext: DeviceContext
        get() = SmartTag

    companion object : DeviceContext {
        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setServiceData(
                    offlineFindingServiceUUID,
                    // First Byte: 13, FF --> After 24 Hours, 12, FE --> After 15 Minutes
                    // Twelve Byte: 04, 00 --> UWB off, 04, 04 --> UWB on
                    byteArrayOf(
                        (0x12).toByte(), (0x00.toByte()), (0x00.toByte()), (0x00.toByte()),
                        (0x00.toByte()), (0x00.toByte()), (0x00.toByte()), (0x00.toByte()),
                        (0x00.toByte()), (0x00.toByte()), (0x00.toByte()), (0x04.toByte())),
                    byteArrayOf(
                        (0xFE).toByte(), (0x6B.toByte()), (0xFA.toByte()), (0x00.toByte()),
                        (0xC8.toByte()), (0x40.toByte()), (0x62.toByte()), (0x8F.toByte()),
                        (0x00.toByte()), (0xE2.toByte()), (0x60.toByte()), (0x00.toByte()))
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.GALAXY_SMART_TAG

        override val defaultDeviceName: String
            get() = "Galaxy SmartTag"

        override val statusByteDeviceType: UInt
            get() = 0u

        val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")
    }
}