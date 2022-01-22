package de.seemoo.at_tracking_detection.database.models.device

import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication

abstract class Device {

    abstract val imageResource: Int

    abstract val defaultDeviceName: String

    abstract val defaultDeviceNameWithId: String

    fun getDrawable(): Drawable? {
        val context = ATTrackingDetectionApplication.getAppContext()
        return AppCompatResources.getDrawable(context, imageResource)
    }
}