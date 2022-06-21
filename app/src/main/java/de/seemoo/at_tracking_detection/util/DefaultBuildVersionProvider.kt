package de.seemoo.at_tracking_detection.util

import android.os.Build
import javax.inject.Inject

class DefaultBuildVersionProvider @Inject constructor() : BuildVersionProvider{
    override fun sdkInt(): Int {
        return Build.VERSION.SDK_INT
    }
}