package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.*
import de.seemoo.at_tracking_detection.util.Util.getBitsFromByte
import timber.log.Timber

class Chipolo(val id: Int) : Device() {
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_baseline_device_unknown_24 // TODO: own icon

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_chipolo)
            .format(id)

    override val deviceContext: DeviceContext
        get() = Chipolo

    companion object : DeviceContext {
        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setServiceUuid(offlineFindingServiceUUID)
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.CHIPOLO

        override val defaultDeviceName: String
            get() = "Chipolo"

        override val statusByteDeviceType: UInt
            get() = 0u

        val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FE33-0000-1000-8000-00805F9B34FB")

        override fun getConnectionState(scanResult: ScanResult): ConnectionState {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null) {
                // The last bit of the second byte indicates the offline mode
                // 0 --> Device was connected in the last 30 minutes
                // 1 --> Last Connection with owner device was longer than 30 minutes ago
                val statusBit = getBitsFromByte(serviceData[1], 0)

                return if (statusBit) {
                    Timber.d("Chipolo: Overmature Offline Mode")
                    ConnectionState.OVERMATURE_OFFLINE
                } else {
                    Timber.d("Chipolo: Premature Offline Mode")
                    ConnectionState.PREMATURE_OFFLINE
                }
            }

            return ConnectionState.UNKNOWN
        }
    }
}