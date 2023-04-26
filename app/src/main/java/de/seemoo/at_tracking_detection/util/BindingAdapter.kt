package de.seemoo.at_tracking_detection.util

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getPublicKey
import java.util.*

@BindingAdapter("setAdapter")
fun RecyclerView.bindRecyclerViewAdapter(adapter: RecyclerView.Adapter<*>) {
    this.run {
        this.setHasFixedSize(true)
        this.adapter = adapter
    }
}

@SuppressLint("UseCompatLoadingForDrawables")
@BindingAdapter("setSignalStrengthDrawable", requireAll = true)
fun setSignalStrengthDrawable(imageView: ImageView, scanResult: ScanResult) {
    val rssi: Int = scanResult.rssi
    val quality = Utility.dbmToQuality(rssi)

    when (quality) {
        0 -> imageView.setImageDrawable(imageView.context.getDrawable(R.drawable.ic_signal_low))
        1 -> imageView.setImageDrawable(imageView.context.getDrawable(R.drawable.ic_signal_middle_low))
        2 -> imageView.setImageDrawable(imageView.context.getDrawable(R.drawable.ic_signal_middle_high))
        3 -> imageView.setImageDrawable(imageView.context.getDrawable(R.drawable.ic_signal_high))
    }
}


@BindingAdapter("setDeviceDrawable", requireAll = true)
fun setDeviceDrawable(imageView: ImageView, scanResult: ScanResult) {
    val device = BaseDevice(scanResult).device
    imageView.setImageDrawable(device.getDrawable())
}

@BindingAdapter("setDeviceColor", requireAll = true)
fun setDeviceColor(materialCardView: MaterialCardView, scanResult: ScanResult) {
    when (BaseDevice.getConnectionState(scanResult)) {
        ConnectionState.CONNECTED -> materialCardView.setCardBackgroundColor(-7829368)
        ConnectionState.PREMATURE_OFFLINE -> materialCardView.setCardBackgroundColor(-7829368)
        ConnectionState.OFFLINE -> materialCardView.setCardBackgroundColor(-7829368)
        ConnectionState.OVERMATURE_OFFLINE -> materialCardView.setCardBackgroundColor(0)
        ConnectionState.UNKNOWN -> materialCardView.setCardBackgroundColor(0)
    }
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

@BindingAdapter("hideWhenNoSoundPlayed", requireAll = true)
fun hideWhenNoSoundPlayed(view: View, scanResult: ScanResult) {
    val device = BaseDevice(scanResult).device
    if (device.isConnectable() && BaseDevice.getConnectionState(scanResult) == ConnectionState.OVERMATURE_OFFLINE) {
        view.visibility = View.VISIBLE
    }else {
        view.visibility = View.GONE
    }
}