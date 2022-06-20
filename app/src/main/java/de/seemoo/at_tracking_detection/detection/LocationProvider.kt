package de.seemoo.at_tracking_detection.detection

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import androidx.core.content.ContextCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.util.BuildVersionProvider
import de.seemoo.at_tracking_detection.util.DefaultBuildVersionProvider
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
open class LocationProvider @Inject constructor(
    private val locationManager: LocationManager,
    private val versionProvider: BuildVersionProvider): LocationListener {

    private val handler: Handler = Handler(Looper.getMainLooper())
    private var locationCallback: ((Location?)->Unit)? = null
//    private val versionProvider = DefaultBuildVersionProvider()


    open fun getLastLocation(): Location? {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return getLastLocationFromAnyProvider()
    }

    /**
     * Fetches the most recent location from network and gps and returns the one that has been recveived more recently
     * @return the most recent location across multiple providers
     */
    @SuppressLint("InlinedApi") // Suppressed, because we use a custom version provider which is injectable for testing
    private fun getLastLocationFromAnyProvider(): Location? {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        if (versionProvider.sdkInt() >= Build.VERSION_CODES.S && locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            //Use the fused location provider
            val fusedLocation = locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)

            // Check if the requirements are satisfied
            if (fusedLocation != null && locationMatchesMinimumRequirements(fusedLocation)) {
                return fusedLocation
            }
            return null
        }else {
            return legacyGetLastLocationFromAnyProvider()
        }
    }

    private fun legacyGetLastLocationFromAnyProvider(): Location? {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        // On older versions we use both providers to get the best location signal
        val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            if (gpsLocation != null && networkLocation != null) {
                // Got to past locations, lets check which passes our requirements
                val gpsRequirements = locationMatchesMinimumRequirements(gpsLocation)
                val networkRequirements = locationMatchesMinimumRequirements(networkLocation)
                if (gpsRequirements && networkRequirements) {
                    // Check which one is more current
                    if (gpsLocation.time > networkLocation.time) {
                        return gpsLocation
                    }else {
                        return networkLocation
                    }
                }else if (gpsRequirements) {
                    // Only GPS satisifies the requirements. Return it
                    return gpsLocation
                }else if (networkRequirements) {
                    // Only network satisfies. Return it
                    return networkLocation
                }else {
                    return null
                }
            }else if (gpsLocation != null && locationMatchesMinimumRequirements(gpsLocation)) {
                // Only gps satisfies and network does not exist
                return gpsLocation
            }
        }

        if (networkLocation != null && locationMatchesMinimumRequirements(networkLocation)) {
            return networkLocation
        }

        return null
    }

    private fun getSecondsSinceLocation(location: Location): Long {
        val millisecondsSinceLocation = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1000000L
        val timeOfLocationevent = System.currentTimeMillis() - millisecondsSinceLocation
        val locationDate = Instant.ofEpochMilli(timeOfLocationevent).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val timeDiff = ChronoUnit.SECONDS.between(locationDate, LocalDateTime.now())

        return timeDiff
    }

    private fun locationMatchesMinimumRequirements(location: Location): Boolean {
       return location.accuracy <= MIN_ACCURACY_METER && getSecondsSinceLocation(location) <= MAX_AGE_SECONDS
    }

    /**
     * Get the current location with a callback
     */
    @SuppressLint("InlinedApi") // Suppressed, because we use a custom version provider which is injectable for testing
    open fun getCurrentLocation(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (versionProvider.sdkInt() >= 31 && locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            Timber.d("Requesting fused location updates")
            this.locationCallback = callback
            locationManager.requestLocationUpdates(
                LocationManager.FUSED_PROVIDER,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                this,
                handler.looper
            )
        }else {
            legacyGetCurrentLocationFromAnyProvider(callback)
        }
    }

    fun legacyGetCurrentLocationFromAnyProvider(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        Timber.d("Requesting legacy location updates")
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

    fun stopLocationUpdates() {
        locationManager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        Timber.d("Location updated: ${location.latitude} ${location.longitude}")
        if (locationMatchesMinimumRequirements(location)) {
            locationCallback?.let { it(location) }
            locationManager.removeUpdates(this)
            locationCallback = null
        }else {
            Timber.d("New location does not satisfy requirements. Waiting for a better one")
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    // Android Phones with SDK < 30 need these methods
    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    companion object {
        const val MIN_UPDATE_TIME_MS = 100L
        const val MIN_DISTANCE_METER = 10.0F
        const val MAX_AGE_SECONDS = 100L
        const val MIN_ACCURACY_METER = 100L
    }

}