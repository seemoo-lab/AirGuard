package de.seemoo.at_tracking_detection.database.models.device.types

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
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.Utility.getBitsFromByte
import timber.log.Timber
import java.util.UUID

class SamsungTracker(val id: Int) : Device() {
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_smarttag_icon

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_samsung_tracker)
            .format(id)

    override val deviceContext: DeviceContext
        get() = SamsungTracker

    companion object : DeviceContext {
        private val GATT_GENERIC_ACCESS_SERVICE = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        private val GATT_DEVICE_NAME_CHARACTERISTIC = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
        private val GATT_APPEARANCE_CHARACTERISTIC = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb")

        private val GATT_DEVICE_INFORMATION_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        private val GATT_MANUFACTURER_NAME_CHARACTERISTIC = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")

        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setServiceData(
                    offlineFindingServiceUUID,
                    byteArrayOf((0x10).toByte()),
                    byteArrayOf((0xF8).toByte())
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.SAMSUNG_TRACKER

        override val websiteManufacturer: String
            get() = "https://www.samsung.com/"

        override val defaultDeviceName: String
            get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.samsung_tracker_name)

        override val statusByteDeviceType: UInt
            get() = 0u

        val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")

        suspend fun getSubType(wrappedScanResult: ScanResultWrapper): SamsungTrackerType {
            val characteristicsToRead = listOf(
                Triple(GATT_GENERIC_ACCESS_SERVICE, GATT_DEVICE_NAME_CHARACTERISTIC, "string"),
                Triple(GATT_GENERIC_ACCESS_SERVICE, GATT_APPEARANCE_CHARACTERISTIC, "int"),
                Triple(GATT_DEVICE_INFORMATION_SERVICE, GATT_MANUFACTURER_NAME_CHARACTERISTIC, "string")
            )

            val resultMap = Utility.connectAndRetrieveCharacteristics(
                ATTrackingDetectionApplication.getAppContext(),
                wrappedScanResult.deviceAddress,
                characteristicsToRead
            )

            val deviceName = resultMap[GATT_DEVICE_NAME_CHARACTERISTIC] as? String
            val appearance = resultMap[GATT_APPEARANCE_CHARACTERISTIC] as? Int
            val manufacturerName = resultMap[GATT_MANUFACTURER_NAME_CHARACTERISTIC] as? String

            val advertisedName = wrappedScanResult.advertisedName
            val hasUWB = wrappedScanResult.uwbCapable

            return when {
                hasUWB == true && (deviceName == "Smart Tag2" || advertisedName == "Smart Tag2" || appearance == 576) -> SamsungTrackerType.SMART_TAG_2
                manufacturerName == "SOLUM" -> SamsungTrackerType.SOLUM
                hasUWB == true && (deviceName == "Smart Tag" || advertisedName == "Smart Tag" || appearance == 512) -> SamsungTrackerType.SMART_TAG_1_PLUS
                hasUWB == false && (deviceName == "Smart Tag" || advertisedName == "Smart Tag" || appearance == 512) -> SamsungTrackerType.SMART_TAG_1
                else -> SamsungTrackerType.UNKNOWN
            }
        }

        fun getUwbAvailability(scanResult: ScanResult): Boolean? {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null && serviceData.size >= 12) {
                return getBitsFromByte(serviceData[12], 2)
            }

            return null
        }

        fun getPropertiesByte(scanResult: ScanResult): Byte? {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null && serviceData.size >= 12) {
                return serviceData[12]
            }

            return null
        }

        fun getInternalAgingCounter(scanResult: ScanResult): ByteArray? {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null && serviceData.size >= 12) {
                Timber.d("Internal Aging Counter: %02X %02X %02X".format(serviceData[3], serviceData[2], serviceData[1]))
                return byteArrayOf(serviceData[3], serviceData[2], serviceData[1])
            }

            return null
        }

        fun convertAgingCounterToInt(agingCounter: ByteArray): Int {
            require(agingCounter.size == 3) { "agingCounter must have exactly 3 bytes" }
            return (agingCounter[0].toInt() and 0xFF shl 16) or
                    (agingCounter[1].toInt() and 0xFF shl 8) or
                    (agingCounter[2].toInt() and 0xFF)
        }

        fun decrementAgingCounter(agingCounter: ByteArray, amount: Int = 1): ByteArray {
            require(agingCounter.size == 3) { "agingCounter must have exactly 3 bytes" }
            var value = convertAgingCounterToInt(agingCounter)
            value -= amount
            value = value.coerceAtLeast(0)
            return ByteArray(3).apply {
                this[0] = ((value shr 16) and 0xFF).toByte()
                this[1] = ((value shr 8) and 0xFF).toByte()
                this[2] = (value and 0xFF).toByte()
            }
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