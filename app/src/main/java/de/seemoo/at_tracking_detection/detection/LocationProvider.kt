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
class LocationProvider @Inject constructor(private val locationManager: LocationManager): LocationListener {

    private val handler: Handler = Handler(Looper.getMainLooper())
    private var locationCallback: ((Location?)->Unit)? = null

    // TODO: Check for permission here. Suppress Lint should not be the option
    @SuppressLint("MissingPermission")
    fun getLastLocation(): Location? {
        Timber.d("Requesting Location...")
        val gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkProviderEnabled =
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (gpsProviderEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                this,
                handler.looper
            )
            return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } else if (networkProviderEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                this,
                handler.looper
            )
            return locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(callback: (Location?) -> Unit) {
        Timber.d("Requesting Location...")
        val gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkProviderEnabled =
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        this.locationCallback = callback

        if (gpsProviderEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                this,
                handler.looper
            )

        } else if (networkProviderEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                this,
                handler.looper
            )
        }
    }

    override fun onLocationChanged(location: Location) {
        Timber.d("Location updated: ${location.latitude} ${location.longitude}")
        locationCallback?.let { it(location) }
        locationManager.removeUpdates(this)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    // Android Phones with SDK < 30 need these methods
    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    companion object {
        const val MIN_UPDATE_TIME_MS = 100L
        const val MIN_DISTANCE_METER = 10.0F
    }
}