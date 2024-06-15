package de.seemoo.at_tracking_detection.database.models.device.types

import android.annotation.SuppressLint
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BatteryState
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.Utility.getBitsFromByte
import timber.log.Timber

class SamsungDevice(val id: Int) : Device() {
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_smarttag_icon

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_smarttag)
            .format(id)

    override val deviceContext: DeviceContext
        get() = SamsungDevice

    companion object : DeviceContext {
        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setServiceData(
                    offlineFindingServiceUUID,
                    // First Byte:
                    // 13, FF --> After 8 Hours,
                    // 12, FE --> After 15 Minutes
                    // 10, F8 --> Connected
                    //
                    // Twelve Byte:
                    // 04, 00 --> UWB off,
                    // 04, 04 --> UWB on
                    byteArrayOf((0x10).toByte()),
                    byteArrayOf((0xF8).toByte())
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.SAMSUNG_DEVICE

        override val websiteManufacturer: String
            get() = "https://www.samsung.com/"

        override val defaultDeviceName: String
            get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.smarttag_no_uwb)

        override val statusByteDeviceType: UInt
            get() = 0u

        val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")

        fun getSubType(wrappedScanResult: ScanResultWrapper): SamsungDeviceType {
            val advertisedName = wrappedScanResult.advertisedName
            val hasUWB = wrappedScanResult.uwbCapable
            val deviceName = wrappedScanResult.deviceName
            val externalManufacturerName = wrappedScanResult.manufacturer // 0x180A, 0x2A29
            val appearance = wrappedScanResult.appearance // 0x1800, 0x2A01, e.g.: SmartTag 2: 576, Solum: 512

            println("Samsung Device: $deviceName, $advertisedName, $hasUWB, $externalManufacturerName, $appearance")

            return if (hasUWB == true && (deviceName == "Smart Tag2" || advertisedName == "Smart Tag2")) {
                SamsungDeviceType.SMART_TAG_2
            } else if (hasUWB == false && externalManufacturerName == "SOLUM") {
                SamsungDeviceType.SOLUM
            } else if (hasUWB == true) {
                SamsungDeviceType.SMART_TAG_1_PLUS
            } else if (hasUWB == false) {
                SamsungDeviceType.SMART_TAG_1
            } else {
                SamsungDeviceType.UNKNOWN
            }
        }

        fun getUwbAvailability(scanResult: ScanResult): Boolean? {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null && serviceData.size >= 12) {
                return getBitsFromByte(serviceData[12], 2)
            }

            return null
        }

        override fun getConnectionState(scanResult: ScanResult): ConnectionState {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null && serviceData.isNotEmpty()) {
                // Little Endian (5,6,7) --> (2,1,0)
                val bit5 = getBitsFromByte(serviceData[0], 2)
                val bit6 = getBitsFromByte(serviceData[0],1)
                val bit7 = getBitsFromByte(serviceData[0],0)

                return if (!bit5 && bit6 && bit7) {
                    Timber.d("Samsung Device in Overmature Offline Mode")
                    ConnectionState.OVERMATURE_OFFLINE
                } else if (!bit5 && bit6 && !bit7) {
                    Timber.d("Samsung: Offline Mode")
                    ConnectionState.OFFLINE
                } else if (!bit5 && !bit6 && bit7) {
                    Timber.d("Samsung: Premature Offline Mode")
                    ConnectionState.PREMATURE_OFFLINE
                } else {
                    ConnectionState.CONNECTED
                }
            }

            return ConnectionState.UNKNOWN
        }

        override fun getBatteryState(scanResult: ScanResult): BatteryState {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null && serviceData.size >= 12) {
                val bit6 = getBitsFromByte(serviceData[12],1)
                val bit7 = getBitsFromByte(serviceData[12],0)

                return if (bit6 && bit7) {
                    Timber.d("Samsung Device Battery State: FULL")
                    BatteryState.FULL
                } else if (bit6 && !bit7) {
                    Timber.d("Samsung Device Battery State: MEDIUM")
                    BatteryState.MEDIUM
                } else if (!bit6 && bit7) {
                    Timber.d("Samsung Device Battery State: LOW")
                    BatteryState.LOW
                } else {
                    Timber.d("Samsung Device Battery State: VERY_LOW")
                    BatteryState.VERY_LOW
                }
            }

            return BatteryState.UNKNOWN
        }

        override fun getPublicKey(scanResult: ScanResult): String {
            try {
                val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

                fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

                if (serviceData != null && serviceData.size >= 12) {
                    return byteArrayOf(serviceData[4], serviceData[5], serviceData[6], serviceData[7], serviceData[8], serviceData[9], serviceData[10], serviceData[11]).toHexString()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting public key")
            }
            return scanResult.device.address
        }
    }
}