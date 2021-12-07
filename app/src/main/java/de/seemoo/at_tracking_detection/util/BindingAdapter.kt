package de.seemoo.at_tracking_detection.util

import android.bluetooth.le.ScanResult
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.tables.Device
import timber.log.Timber
import java.util.*
import kotlin.math.pow
import kotlin.math.round

@BindingAdapter("setAdapter")
fun RecyclerView.bindRecyclerViewAdapter(adapter: RecyclerView.Adapter<*>) {
    this.run {
        this.setHasFixedSize(true)
        this.adapter = adapter
    }
}

@BindingAdapter("setDistance", requireAll = true)
fun setDistance(textView: TextView, scanResult: ScanResult) {
    var txPowerLevel = scanResult.scanRecord?.txPowerLevel
    if (txPowerLevel == null || txPowerLevel == Int.MIN_VALUE) {
        txPowerLevel = -69
    }
    val distance = 10F.pow(((txPowerLevel - scanResult.rssi) / (10 * 2)))
    if (Locale.getDefault() == Locale.US) {
        textView.text = "%.1f FT".format(distance * 3.2808)
    } else {
        textView.text = "%.1f M".format(distance)
    }

}

@BindingAdapter("setDeviceDrawable", requireAll = true)
fun setDeviceDrawable(imageView: ImageView, scanResult: ScanResult) {
    val device = Device(scanResult)
    val context = ATTrackingDetectionApplication.getAppContext()
    val drawable = ContextCompat.getDrawable(context, device.getImage())
    imageView.setImageDrawable(drawable)
}

@BindingAdapter("setDeviceName", requireAll = true)
fun setDeviceName(textView: TextView, scanResult: ScanResult) {
    val device = Device(scanResult)
    var deviceName = scanResult.scanRecord?.deviceName
    if (deviceName.isNullOrEmpty()) {
        deviceName = device.getDeviceName()
    }
    textView.text = deviceName
}