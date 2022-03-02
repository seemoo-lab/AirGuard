package de.seemoo.at_tracking_detection.util

import android.bluetooth.le.ScanResult
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import java.util.*
import kotlin.math.pow

@BindingAdapter("setAdapter")
fun RecyclerView.bindRecyclerViewAdapter(adapter: RecyclerView.Adapter<*>) {
    this.run {
        this.setHasFixedSize(true)
        this.adapter = adapter
    }
}

@BindingAdapter("setDistance", requireAll = true)
fun setDistance(textView: TextView, scanResult: ScanResult) {
    val context = ATTrackingDetectionApplication.getAppContext()
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val useMetric = sharedPreferences.getBoolean("use_metric", Locale.getDefault() != Locale.US)

    var txPowerLevel = scanResult.scanRecord?.txPowerLevel
    if (txPowerLevel == null || txPowerLevel == Int.MIN_VALUE) {
        txPowerLevel = -69
    }
    val distance = 10F.pow(((txPowerLevel - scanResult.rssi) / (10 * 2)))

    if (!useMetric) {
        textView.text = "%.1f FT".format(distance * 3.2808)
    } else {
        textView.text = "%.1f M".format(distance)
    }

}

@BindingAdapter("setDeviceDrawable", requireAll = true)
fun setDeviceDrawable(imageView: ImageView, scanResult: ScanResult) {
    val device = BaseDevice(scanResult).device
    imageView.setImageDrawable(device.getDrawable())
}

@BindingAdapter("setDeviceName", requireAll = true)
fun setDeviceName(textView: TextView, scanResult: ScanResult) {
    val device =  BaseDevice(scanResult).device
    textView.text = device.deviceContext.defaultDeviceName
}