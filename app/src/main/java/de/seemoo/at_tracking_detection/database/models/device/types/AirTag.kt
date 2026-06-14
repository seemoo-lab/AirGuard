package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType

class AirTag(id: Int) : AppleFindMy(id) {

    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_airtag

    override val soundProtocolPriority: List<SoundProtocol>
        get() = listOf(SoundProtocol.AIRTAG, SoundProtocol.DULT, SoundProtocol.FINDMY)

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources
            .getString(R.string.device_name_airtag).format(id)

    override val deviceContext: DeviceContext
        get() = AirTag

    companion object : DeviceContext {
        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setManufacturerData(
                    0x4C,
                    // Only Offline Devices:
                    // byteArrayOf((0x12).toByte(), (0x19).toByte(), (0x10).toByte()),
                    // byteArrayOf((0xFF).toByte(), (0xFF).toByte(), (0x18).toByte())
                    // All Devices:
                    byteArrayOf((0x12).toByte(), (0x19).toByte(), (0x10).toByte()),
                    byteArrayOf((0xFF).toByte(), (0x00).toByte(), (0x18).toByte())
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.AIRTAG

        override val defaultDeviceName: String
            get() = ATTrackingDetectionApplication.getAppContext().resources
                .getString(R.string.airtag_default_name)

        override val statusByteDeviceType: UInt
            get() = 1u

        override val websiteManufacturer: String
            get() = "https://www.apple.com/airtag/"

        override fun getBatteryState(scanResult: ScanResult) =
            AppleFindMy.getBatteryState(scanResult)

        override fun getConnectionState(scanResult: ScanResult) =
            AppleFindMy.getConnectionState(scanResult)
    }
}