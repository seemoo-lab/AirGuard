package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BatteryState
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.util.Utility.getBitsFromByte

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
                    byteArrayOf((0x12).toByte(), (0x19).toByte()),
                    byteArrayOf((0xFF).toByte(), (0x00).toByte())
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
                return if (mfg[1] == (0x19).toByte()) {
                    ConnectionState.OVERMATURE_OFFLINE
                } else {
                    ConnectionState.CONNECTED
                }
            }

            return ConnectionState.UNKNOWN
        }

        override fun getBatteryState(scanResult: ScanResult): BatteryState {
            val mfg: ByteArray? = scanResult.scanRecord?.getManufacturerSpecificData(0x4C)

            println(mfg?.contentToString())
            println(mfg?.size)
            if (mfg != null && mfg.size >= 3) {
//                for (i in 0..7) {
//                    println(getBitsFromByte(mfg[2], i))
//                }

                // TODO
                // Real AirTag Data for Status: 00 00 10 00
                // This deviates from the documentation

                if (mfg != null && mfg.size >= 3) {
                    val status = mfg[2] // Extract the status byte

                    if (getBitsFromByte(status, 2)) {
                        // Bits 6-7: Battery level
                        val batteryLevel = (status.toInt() shr 6) and 0x03

                        when (batteryLevel) {
                            0x03 -> return BatteryState.FULL
                            0x02 -> return BatteryState.MEDIUM
                            0x01 -> return BatteryState.LOW
                            0x00 -> return BatteryState.VERY_LOW
                        }
                    }
                }

//                if (getBitsFromByte(mfg[2], 2)){
//                    // 11
//                    if (getBitsFromByte(mfg[2], 6) && getBitsFromByte(mfg[2], 7)) {
//                        return BatteryState.FULL
//                    // 10
//                    } else if (getBitsFromByte(mfg[2], 6) && !getBitsFromByte(mfg[2], 7)) {
//                        return BatteryState.MEDIUM
//                    // 01
//                    } else if (!getBitsFromByte(mfg[2], 6) && getBitsFromByte(mfg[2], 7)) {
//                        return BatteryState.LOW
//                    // 00
//                    } else {
//                        return BatteryState.VERY_LOW
//                    }
//                }
            }

            return BatteryState.UNKNOWN
        }
//        override fun getBatteryState(scanResult: ScanResult): BatteryState {
//            fun getBitsFromByte2(value: Int, position: Int): Boolean {
//                // This uses Little Endian
//                return ((value shr position) and 1) == 1
//            }
//            val mfg: ByteArray? = scanResult.scanRecord?.getManufacturerSpecificData(0x4C)
//
//            println(mfg?.contentToString())
//            println(mfg?.size)
//
//            if (mfg != null && mfg.size >= 3) {
//                val status = mfg[2].toInt() // Extract the status byte
//
//                if (getBitsFromByte2(status, 2)) {
//                    // Bits 6-7: Battery level
//                    val batteryLevel = (status shr 6) and 0x03
//
//                    when (batteryLevel) {
//                        0x03 -> return BatteryState.FULL
//                        0x02 -> return BatteryState.MEDIUM
//                        0x01 -> return BatteryState.LOW
//                        0x00 -> return BatteryState.VERY_LOW
//                    }
//                }
//            }
//
//            return BatteryState.UNKNOWN
//        }
    }
}