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
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungDeviceType
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

    val drawableResId = if (deviceFromDb != null && deviceFromDb.subDeviceType != "UNKNOWN" && deviceFromDb.deviceType == DeviceType.SAMSUNG_DEVICE) {
        val subTypeString = deviceFromDb.subDeviceType
        val subType = SamsungDeviceType.stringToSubType(subTypeString)
        SamsungDeviceType.drawableForSubType(subType)
    } else {
        DeviceType.getImageDrawable(wrappedScanResult)
    }

    val drawable = ContextCompat.getDrawable(imageView.context, drawableResId)
    imageView.setImageDrawable(drawable)
}

@BindingAdapter("setDeviceName", requireAll = true)
fun setDeviceName(textView: TextView, wrappedScanResult: ScanResultWrapper) {
    val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
    val deviceFromDb = deviceRepository.getDevice(wrappedScanResult.uniqueIdentifier)

    if (deviceFromDb?.name != null) {
        textView.text = deviceFromDb.getDeviceNameWithID()
    } else if (deviceFromDb != null && deviceFromDb.subDeviceType != "UNKNOWN" && deviceFromDb.deviceType == DeviceType.SAMSUNG_DEVICE) {
        val subTypeString = deviceFromDb.subDeviceType
        val subType = SamsungDeviceType.stringToSubType(subTypeString)
        textView.text = SamsungDeviceType.visibleStringFromSubtype(subType)
    } else {
        textView.text = DeviceType.userReadableName(wrappedScanResult)
    }
}

