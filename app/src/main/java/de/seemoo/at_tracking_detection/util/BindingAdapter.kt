package de.seemoo.at_tracking_detection.util

import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
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
    val rssi: Int = wrappedScanResult.rssi
    val quality = Utility.dbmToQuality(rssi)

    when (quality) {
        0 -> imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.ic_signal_low))
        1 -> imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.ic_signal_middle_low))
        2 -> imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.ic_signal_middle_high))
        3 -> imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.ic_signal_high))
    }
}


@BindingAdapter("setDeviceDrawable", requireAll = true)
fun setDeviceDrawable(imageView: ImageView, wrappedScanResult: ScanResultWrapper) {
    val drawableResId = DeviceType.getImageDrawable(wrappedScanResult.deviceType)
    val drawable = ContextCompat.getDrawable(imageView.context, drawableResId)
    imageView.setImageDrawable(drawable)
}

@BindingAdapter("setDeviceName", requireAll = true)
fun setDeviceName (textView: TextView, wrappedScanResult: ScanResultWrapper) {
    val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
    val deviceFromDb = deviceRepository.getDevice(wrappedScanResult.uniqueIdentifier)
    if (deviceFromDb?.name != null) {
        textView.text = deviceFromDb.getDeviceNameWithID()
    } else {
        // TODO: this can be optimized
        val device =  BaseDevice(wrappedScanResult.scanResult).device
        textView.text = device.deviceContext.defaultDeviceName
    }
}

