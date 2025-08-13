package de.seemoo.at_tracking_detection.util.ble

import android.content.Context
import android.location.LocationManager
import android.os.Build
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication

object LocationStateMonitor {
    fun isLocationEnabled(): Boolean {
        val ctx = ATTrackingDetectionApplication.getAppContext()
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }
}
