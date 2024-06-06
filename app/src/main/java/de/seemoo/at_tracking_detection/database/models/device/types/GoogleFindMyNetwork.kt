package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid
import android.bluetooth.le.ScanResult
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.util.Utility
import timber.log.Timber

class GoogleFindMyNetwork(val id: Int) : Device() {

    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_chipolo

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_find_my_device_google)
            .format(id)

    override val deviceContext: DeviceContext
        get() = GoogleFindMyNetwork

    companion object : DeviceContext {
        override val deviceType: DeviceType
            get() = DeviceType.GOOGLE_FIND_MY_NETWORK

        override val defaultDeviceName: String
            get() = "\"Find My Device\" Device"

        override val websiteManufacturer: String
            get() = "https://www.google.com/android/find/"

        override val statusByteDeviceType: UInt
            get() = 0u

        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setServiceData(
                    offlineFindingServiceUUID,
                    byteArrayOf((0x40).toByte()),
                    byteArrayOf((0x00).toByte()))
                .build()

        val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")

        override fun getConnectionState(scanResult: ScanResult): ConnectionState {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null) {
                // The last bit of the first byte indicates the offline mode
                // 0 --> Device was connected in the last 4 hours
                // 1 --> Last Connection with owner device was longer than 4 hours ago

                val statusBit = Utility.getBitsFromByte(serviceData[0], 0)

                return if (statusBit) {
                    Timber.d("Google Find My: Overmature Offline Mode")
                    ConnectionState.OVERMATURE_OFFLINE
                } else {
                    Timber.d("Google Find My: Premature Offline Mode")
                    ConnectionState.PREMATURE_OFFLINE
                }
            }

            return ConnectionState.UNKNOWN
        }
    }

}