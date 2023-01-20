package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType

class AppleDevice(val id: Int) : Device() {
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_baseline_device_unknown_24

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_apple_device)
            .format(id)

    override val deviceContext: DeviceContext
        get() = AppleDevice

    companion object : DeviceContext {
        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setManufacturerData(
                    0x4C,
                    // Only Offline Devices:
                    // byteArrayOf((0x12).toByte(), (0x19).toByte(), (0x00).toByte()),
                    // byteArrayOf((0xFF).toByte(), (0xFF).toByte(), (0x18).toByte())
                    // All Devices:
                    byteArrayOf((0x12).toByte(), (0x19).toByte(), (0x00).toByte()),
                    byteArrayOf((0xFF).toByte(), (0x00).toByte(), (0x18).toByte())
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.APPLE

        override val defaultDeviceName: String
            get() = "Apple Device"

        override val minTrackingTime: Int
            get() = 150 * 60

        override val statusByteDeviceType: UInt
            get() = 0u

        override fun getConnectionState(scanResult: ScanResult): ConnectionState {
            val mfg: ByteArray? = scanResult.scanRecord?.getManufacturerSpecificData(0x4C)

            if (mfg != null && mfg.size > 2) {
                return if (mfg[1] == (0x19).toByte()){
                    ConnectionState.OVERMATURE_OFFLINE
                } else {
                    ConnectionState.CONNECTED
                }
            }

            return ConnectionState.UNKNOWN
        }
    }
}