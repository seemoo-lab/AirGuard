package de.seemoo.at_tracking_detection.detection

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class LocationProvider @Inject constructor(private val locationManager: LocationManager) {

    private val handler: Handler = Handler(Looper.getMainLooper())

    // TODO: Check for permission here. Suppress Lint should not be the option
    @SuppressLint("MissingPermission")
    fun getCurrentLocation(): Location? {
        Timber.d("Requesting Location...")
        val gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkProviderEnabled =
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (gpsProviderEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                DetectionLocationListener(),
                handler.looper
            )
            return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } else if (networkProviderEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                DetectionLocationListener(),
                handler.looper
            )
            return locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        return null
    }

    class DetectionLocationListener : LocationListener {
        override fun onLocationChanged(location: Location) {
            Timber.d("Location: ${location.latitude} ${location.longitude}")
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        // Android Phones with SDK < 30 need these methods
        override fun onProviderEnabled(provider: String) {
        }

        override fun onProviderDisabled(provider: String) {
        }
    }

    companion object {
        const val MIN_UPDATE_TIME_MS = 100L
        const val MIN_DISTANCE_METER = 100.0F
    }
}