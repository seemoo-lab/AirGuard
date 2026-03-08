package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.le.ScanFilter
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import java.util.*

class AirPods(id: Int) : AppleFindMy(id) {

    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_airpods

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_airpods)
            .format(id)

    override val deviceContext: DeviceContext
        get() = AirPods

    override val soundService: String
        get() = AIRPODS_SOUND_SERVICE

    override val soundCharacteristic: UUID
        get() = AIRPODS_SOUND_CHARACTERISTIC

    override val startSoundOpcode: ByteArray
        get() = AIRPODS_START_SOUND_OPCODE

    override val stopSoundOpcode: ByteArray
        get() = AIRPODS_STOP_SOUND_OPCODE


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
                    // Only Offline Devices:
                    // byteArrayOf((0x12).toByte(), (0x19).toByte(), (0x18).toByte()), // Empty status byte?
                    // byteArrayOf((0xFF).toByte(), (0xFF).toByte(), (0x18).toByte()) // ff?
                    // All Devices:
                    byteArrayOf((0x12).toByte(), (0x19).toByte(), (0x18).toByte()),
                    byteArrayOf((0xFF).toByte(), (0x00).toByte(), (0x18).toByte())
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.AIRPODS

        override val websiteManufacturer: String
            get() = "https://www.apple.com/airpods/"

        override val defaultDeviceName: String
            get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.airpods_default_name)

        override val statusByteDeviceType: UInt
            get() = 3u
    }
}