package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.ui.scan.ScanFragment
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.Utility.getBitsFromByte
import timber.log.Timber
import java.util.UUID

class SamsungFindMyMobile(val id: Int) : Device()  {
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_baseline_device_unknown_24

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_samsung_device)
            .format(id)

    override val deviceContext: DeviceContext
        get() = SamsungFindMyMobile

    companion object : DeviceContext {
        private val GATT_GENERIC_ACCESS_SERVICE = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        private val GATT_DEVICE_NAME_CHARACTERISTIC = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")

        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder().setServiceUuid(offlineFindingServiceUUID).build() // TODO

        override val deviceType: DeviceType
            get() = DeviceType.SAMSUNG_FIND_MY_MOBILE

        override val websiteManufacturer: String
            get() = "https://www.samsung.com/"

        override val defaultDeviceName: String
            get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.samsung_find_my_mobile_name)

        override val statusByteDeviceType: UInt
            get() = 0u

        val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FD69-0000-1000-8000-00805F9B34FB")

        fun getUWBAvailability(scanResult: ScanResult): Boolean? {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null && serviceData.size >= 13) {
                return getBitsFromByte(serviceData[13], 2)
            }

            return null
        }

        fun getPropertiesByte(scanResult: ScanResult): Byte? {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null && serviceData.size >= 13) {
                return serviceData[13]
            }

            return null
        }

        override fun getPublicKey(scanResult: ScanResult): String {
            try {
                val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

                fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

                if (serviceData != null && serviceData.size >= 12) {
                    return byteArrayOf(serviceData[1], serviceData[2], serviceData[3], serviceData[4], serviceData[5], serviceData[6], serviceData[7], serviceData[8], serviceData[9], serviceData[10], serviceData[11], serviceData[12]).toHexString()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting public key")
            }
            return scanResult.device.address
        }

        override fun getConnectionState(scanResult: ScanResult): ConnectionState {
            Timber.d("Samsung Find My Mobile Device in Overmature Offline Mode (automatically)")
            return ConnectionState.OVERMATURE_OFFLINE
        }

        suspend fun getSubTypeName(wrappedScanResult: ScanResultWrapper): String {
            val characteristicsToRead = listOf(
                Triple(GATT_GENERIC_ACCESS_SERVICE, GATT_DEVICE_NAME_CHARACTERISTIC, "string")
            )

            val deviceName = Utility.connectAndRetrieveCharacteristics(
                ATTrackingDetectionApplication.getAppContext(),
                wrappedScanResult.deviceAddress,
                characteristicsToRead
            )[GATT_DEVICE_NAME_CHARACTERISTIC] as? String

            return if (!deviceName.isNullOrEmpty()) {
                ScanFragment.deviceNameMap[wrappedScanResult.uniqueIdentifier] = deviceName
                deviceName
            } else {
                ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.samsung_find_my_mobile_name)
            }

        }

    }
}