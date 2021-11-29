package de.seemoo.at_tracking_detection.detection

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class LocationProvider @Inject constructor(private val locationManager: LocationManager): LocationListener {

    private val handler: Handler = Handler(Looper.getMainLooper())
    private var locationCallback: ((Location?)->Unit)? = null


    fun getLastLocation(): Location? {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

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
            return getMostCurrentLocation()
        } else if (networkProviderEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                this,
                handler.looper
            )
            return getMostCurrentLocation()
        }
        return null
    }

    /**
     * Fetches the most recent location from network and gps and returns the one that has been recveived more recently
     * @return the most recent location across multiple providers
     */
    private fun getMostCurrentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (gpsProviderEnabled) {
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            return if (gpsLocation?.time ?: 0 > networkLocation?.time ?: 0) {
                gpsLocation
            }else {
                networkLocation
            }
        }

        return locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Get the current location with a callback
     */
    fun getCurrentLocation(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        Timber.d("Requesting Location...")
        val gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkProviderEnabled =
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        this.locationCallback = callback

        if (gpsProviderEnabled) {
            // Using GPS and Network provider, because the GPS provider does notwork indoors (it will never call the callback)
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                this,
                handler.looper
            )

            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
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
        locationCallback = null
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