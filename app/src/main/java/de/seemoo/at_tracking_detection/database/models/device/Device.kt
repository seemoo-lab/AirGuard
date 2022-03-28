package de.seemoo.at_tracking_detection.database.models.device

import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication

abstract class Device {

    abstract val imageResource: Int

    abstract val defaultDeviceNameWithId: String

    abstract val deviceContext: DeviceContext

    fun getDrawable(): Drawable? {
        val context = ATTrackingDetectionApplication.getAppContext()
        return AppCompatResources.getDrawable(context, imageResource)
    }

    fun isConnectable(): Boolean {
        return this is Connectable
    }
}