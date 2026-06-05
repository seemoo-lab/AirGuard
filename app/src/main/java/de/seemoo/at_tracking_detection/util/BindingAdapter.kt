package de.seemoo.at_tracking_detection.util

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.Observable
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTrackerType
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.ble.DeviceSubTypeDetector

@BindingAdapter("setAdapter")
fun RecyclerView.bindRecyclerViewAdapter(adapter: RecyclerView.Adapter<*>) {
    this.run {
        this.setHasFixedSize(false)
        this.adapter = adapter
    }
}

@BindingAdapter("setDetectionStatus")
fun setDetectionStatus(view: View, status: ScanResultWrapper.DetectionStatus) {
    view.clearAnimation()
    view.alpha = 1.0f

    when (status) {
        ScanResultWrapper.DetectionStatus.QUEUED -> {
            // Slow shimmering effect: Is in Queue to connect
            val anim = AlphaAnimation(1.0f, 0.5f).apply {
                duration = 1000
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            view.startAnimation(anim)
        }
        ScanResultWrapper.DetectionStatus.CONNECTING -> {
            // Fast shimmering effect: Is currently connecting / reading property
            val anim = AlphaAnimation(1.0f, 0.2f).apply {
                duration = 400
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            view.startAnimation(anim)
        }
        ScanResultWrapper.DetectionStatus.IDLE -> {
            // No animation / Animation cleared
        }
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
    val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository
        ?: error("ATTrackingDetectionApplication not initialized")
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
    val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository
        ?: error("ATTrackingDetectionApplication not initialized")
    val deviceFromDb = deviceRepository.getDevice(wrappedScanResult.uniqueIdentifier)

    textView.text = if (deviceFromDb != null) {
        // Case: device is in DB

        if (deviceFromDb.name != null) {
            deviceFromDb.getDeviceNameWithID()
        } else if (deviceFromDb.deviceType == DeviceType.SAMSUNG_TRACKER && deviceFromDb.subDeviceType != "UNKNOWN") {
            val subTypeString = deviceFromDb.subDeviceType
            val subType = SamsungTrackerType.stringToSubType(subTypeString)
            DeviceSubTypeDetector.samsungSubDeviceTypeMap[wrappedScanResult.uniqueIdentifier] = subType
            SamsungTrackerType.visibleStringFromSubtype(subType)
        } else if (deviceFromDb.deviceType == DeviceType.GOOGLE_FIND_MY_NETWORK) {
            val subTypeString = deviceFromDb.subDeviceType
            val subType = GoogleFindMyNetworkType.stringToSubType(subTypeString)
            DeviceSubTypeDetector.googleSubDeviceTypeMap[wrappedScanResult.uniqueIdentifier] = subType
            GoogleFindMyNetworkType.visibleStringFromSubtype(subType)
        } else {
            // Fallback
            DeviceType.userReadableNameDefault(wrappedScanResult.deviceType)
        }
    } else {
        // Case: device ist not in DB
        // There is a possibility that the device has been determined. In that case this is only saved in the temporary map

        if (DeviceSubTypeDetector.samsungSubDeviceTypeMap.containsKey(wrappedScanResult.uniqueIdentifier)) {
            val subType = DeviceSubTypeDetector.samsungSubDeviceTypeMap[wrappedScanResult.uniqueIdentifier]!!
            SamsungTrackerType.visibleStringFromSubtype(subType)
        } else if (DeviceSubTypeDetector.googleSubDeviceTypeMap.containsKey(wrappedScanResult.uniqueIdentifier)) {
            val subType = DeviceSubTypeDetector.googleSubDeviceTypeMap[wrappedScanResult.uniqueIdentifier]!!
            GoogleFindMyNetworkType.visibleStringFromSubtype(subType)
        } else if (DeviceSubTypeDetector.deviceNameMap.containsKey(wrappedScanResult.uniqueIdentifier)) {
            DeviceSubTypeDetector.deviceNameMap[wrappedScanResult.uniqueIdentifier]
        } else {
            // Fallback
            DeviceType.userReadableNameDefault(wrappedScanResult.deviceType)
        }
    }
}

@BindingAdapter("riskColorRes")
fun setCardBackgroundColor(view: MaterialCardView, @ColorRes colorRes: Int) {
    if (colorRes != 0) {
        val color = ContextCompat.getColor(view.context, colorRes)
        view.setCardBackgroundColor(color)
    }
}