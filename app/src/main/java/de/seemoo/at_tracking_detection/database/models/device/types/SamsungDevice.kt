package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.*
import de.seemoo.at_tracking_detection.util.Utility.getBitsFromByte
import timber.log.Timber

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
                    // First Byte:
                    // 13, FF --> After 24 Hours,
                    // 12, FE --> After 15 Minutes
                    // 10, F8 --> Instant
                    //
                    // Twelve Byte:
                    // 04, 00 --> UWB off,
                    // 04, 04 --> UWB on
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

        override fun getConnectionState(scanResult: ScanResult): ConnectionState {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null) {
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

        fun getSamsungDeviceType(scanResult: ScanResult): DeviceType{
            val serviceData = scanResult.scanRecord?.getServiceData(SmartTag.offlineFindingServiceUUID)

            /*
            if (serviceData != null) {
                println("Service Data Byte: ")
                println(String.format("%02X", serviceData[12]))
                println("Service Data Bit for UWB: ")
                println(getBitsFromByte(serviceData[12], 2))
            }
             */

            return if (serviceData == null){
                Timber.d("Samsung Service Data is null")
                deviceType
            // Little Endian: (5) -> (2)
            } else if (getBitsFromByte(serviceData[12], 2)) {
                Timber.d("Samsung Service Data is SmartTag Plus")
                SmartTagPlus.deviceType
            } else {
                Timber.d("Samsung Service Data is SmartTag")
                SmartTag.deviceType
            }
        }

        // TODO: move to Base device
        fun getPublicKey(scanResult: ScanResult): String{
            val serviceData = scanResult.scanRecord?.getServiceData(SmartTag.offlineFindingServiceUUID)

            fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

            return if (serviceData == null || serviceData.size < 12) {
                scanResult.device.address
            } else {
                byteArrayOf(serviceData[4], serviceData[5], serviceData[6], serviceData[7], serviceData[8], serviceData[9], serviceData[10], serviceData[11]).toHexString()
            }
        }
    }

}