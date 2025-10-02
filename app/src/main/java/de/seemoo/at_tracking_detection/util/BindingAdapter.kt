package de.seemoo.at_tracking_detection.util

import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.Observable
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTrackerType
import de.seemoo.at_tracking_detection.ui.scan.ScanFragment
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper

@BindingAdapter("setAdapter")
fun RecyclerView.bindRecyclerViewAdapter(adapter: RecyclerView.Adapter<*>) {
    this.run {
        this.setHasFixedSize(false)
        this.adapter = adapter
    }
}

@BindingAdapter("setSignalStrengthDrawable", requireAll = true)
fun setSignalStrengthDrawable(imageView: ImageView, wrappedScanResult: ScanResultWrapper) {
    fun setImage(rssiValue: Int) {
        val quality = Utility.dbmToQuality(rssiValue)

        when (quality) {
            0 -> imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.ic_signal_low))
            1 -> imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.ic_signal_middle_low))
            2 -> imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.ic_signal_middle_high))
            3 -> imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.ic_signal_high))
        }
    }

    setImage(wrappedScanResult.rssiValue)

    wrappedScanResult.rssi.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            setImage(wrappedScanResult.rssi.get() ?: wrappedScanResult.rssiValue)
        }
    })
}


@BindingAdapter("setDeviceDrawable")
fun setDeviceDrawable(imageView: ImageView, wrappedScanResult: ScanResultWrapper) {
    val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
    val deviceFromDb = deviceRepository.getDevice(wrappedScanResult.uniqueIdentifier)

    val drawable = if (deviceFromDb != null) {
        deviceFromDb.getDrawable()
    } else {
        DeviceType.getImageDrawable(wrappedScanResult).let { ContextCompat.getDrawable(imageView.context, it) }
    }
    imageView.setImageDrawable(drawable)
}

@BindingAdapter("setDeviceName", requireAll = true)
fun setDeviceName(textView: TextView, wrappedScanResult: ScanResultWrapper) {
    val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
    val deviceFromDb = deviceRepository.getDevice(wrappedScanResult.uniqueIdentifier)

    textView.text = if (deviceFromDb != null) {
        // Case: device is in DB

        if (deviceFromDb.name != null) {
            deviceFromDb.getDeviceNameWithID()
        } else if (deviceFromDb.deviceType == DeviceType.SAMSUNG_TRACKER && deviceFromDb.subDeviceType != "UNKNOWN") {
            val subTypeString = deviceFromDb.subDeviceType
            val subType = SamsungTrackerType.stringToSubType(subTypeString)
            ScanFragment.samsungSubDeviceTypeMap[wrappedScanResult.uniqueIdentifier] = subType
            SamsungTrackerType.visibleStringFromSubtype(subType)
        } else if (deviceFromDb.deviceType == DeviceType.GOOGLE_FIND_MY_NETWORK) {
            val subTypeString = deviceFromDb.subDeviceType
            val subType = GoogleFindMyNetworkType.stringToSubType(subTypeString)
            ScanFragment.googleSubDeviceTypeMap[wrappedScanResult.uniqueIdentifier] = subType
            GoogleFindMyNetworkType.visibleStringFromSubtype(subType)
        } else {
            // Fallback
            DeviceType.userReadableNameDefault(wrappedScanResult.deviceType)
        }
    } else {
        // Case: device ist not in DB
        // There is a possibility that the device has been determined. In that case this is only saved in the temporary map

        if (ScanFragment.samsungSubDeviceTypeMap.containsKey(wrappedScanResult.uniqueIdentifier)) {
            val subType = ScanFragment.samsungSubDeviceTypeMap[wrappedScanResult.uniqueIdentifier]!!
            SamsungTrackerType.visibleStringFromSubtype(subType)
        } else if (ScanFragment.googleSubDeviceTypeMap.containsKey(wrappedScanResult.uniqueIdentifier)) {
            val subType = ScanFragment.googleSubDeviceTypeMap[wrappedScanResult.uniqueIdentifier]!!
            GoogleFindMyNetworkType.visibleStringFromSubtype(subType)
        } else if (ScanFragment.deviceNameMap.containsKey(wrappedScanResult.uniqueIdentifier)) {
            ScanFragment.deviceNameMap[wrappedScanResult.uniqueIdentifier]
        } else {
            // Fallback
            DeviceType.userReadableNameDefault(wrappedScanResult.deviceType)
        }
    }
}

