package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType

class Chipolo(val id: Int) : Device() {
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_chipolo

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
            get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.chipolo_default_name)

        override val statusByteDeviceType: UInt
            get() = 0u

        override val websiteManufacturer: String
            get() = "https://chipolo.net/"

        val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FE33-0000-1000-8000-00805F9B34FB")
    }
}