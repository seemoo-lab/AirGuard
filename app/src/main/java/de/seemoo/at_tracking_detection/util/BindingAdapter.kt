package de.seemoo.at_tracking_detection.util

import android.bluetooth.le.ScanResult
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getPublicKey
import java.util.*

@BindingAdapter("setAdapter")
fun RecyclerView.bindRecyclerViewAdapter(adapter: RecyclerView.Adapter<*>) {
    this.run {
        this.setHasFixedSize(true)
        this.adapter = adapter
    }
}

@BindingAdapter("setSignalStrengthDrawable", requireAll = true)
fun setSignalStrengthDrawable(imageView: ImageView, scanResult: ScanResult) {
    val rssi: Int = scanResult.rssi
    val quality = Utility.dbmToQuality(rssi)

    when (quality) {
        0 -> imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.ic_signal_low))
        1 -> imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.ic_signal_middle_low))
        2 -> imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.ic_signal_middle_high))
        3 -> imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.ic_signal_high))
    }
}


@BindingAdapter("setDeviceDrawable", requireAll = true)
fun setDeviceDrawable(imageView: ImageView, scanResult: ScanResult) {
    val device = BaseDevice(scanResult).device
    imageView.setImageDrawable(device.getDrawable())
}

@BindingAdapter("setDeviceName", requireAll = true)
fun setDeviceName (textView: TextView, scanResult: ScanResult) {
    val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository
    val deviceFromDb = deviceRepository?.getDevice(getPublicKey(scanResult))
    if (deviceFromDb?.name != null) {
        textView.text = deviceFromDb.getDeviceNameWithID()
    } else {
        val device =  BaseDevice(scanResult).device
        textView.text = device.deviceContext.defaultDeviceName
    }
}

