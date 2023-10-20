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
import de.seemoo.at_tracking_detection.util.BuildVersionProvider
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
open class LocationProvider @Inject constructor(
    private val locationManager: LocationManager,
    private val versionProvider: BuildVersionProvider): LocationListener {

    private val handler: Handler = Handler(Looper.getMainLooper())
    private var bestLastLocation: Location? = null

    private val locationRequesters = ArrayList<LocationRequester>()

    open fun getLastLocation(checkRequirements: Boolean = true): Location? {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return getLastLocationFromAnyProvider(checkRequirements)
    }

    /**
     * Fetches the most recent location from network and gps and returns the one that has been recveived more recently
     * @return the most recent location across multiple providers
     */
    @SuppressLint("InlinedApi") // Suppressed, because we use a custom version provider which is injectable for testing
    private fun getLastLocationFromAnyProvider(checkRequirements: Boolean): Location? {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val bestLocation = bestLastLocation
        if (bestLocation != null && locationMatchesMinimumRequirements(bestLocation)) {
            return bestLocation
        }

        // The fused location provider does not work reliably with Samsung + Android 12
        // We just stay with the legacy location, because this just works
        val lastLocation = legacyGetLastLocationFromAnyProvider(checkRequirements)

        if (lastLocation != null && bestLocation != null && !checkRequirements) {
            if (lastLocation.time > bestLocation.time) {
                return lastLocation
            }
            return bestLocation
        }
        return lastLocation
    }

    private fun legacyGetLastLocationFromAnyProvider(checkRequirements: Boolean): Location? {
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
                    // Only GPS satisfies the requirements. Return it
                    return gpsLocation
                }else if (networkRequirements) {
                    // Only network satisfies. Return it
                    return networkLocation
                }else if (!checkRequirements) {
                    if (gpsLocation.time > networkLocation.time) {
                        return gpsLocation
                    }
                    return networkLocation
                }
            }else if (gpsLocation != null && locationMatchesMinimumRequirements(gpsLocation)) {
                // Only gps satisfies and network does not exist
                return gpsLocation
            }
        }

        if (networkLocation != null && locationMatchesMinimumRequirements(networkLocation)) {
            return networkLocation
        }else if (!checkRequirements) {
            return networkLocation
        }

        Timber.d("No last know location matched the requirements")
        return null
    }

    private fun getSecondsSinceLocation(location: Location): Long {
        val locationTime = location.time
        val currentTime = Date().time
        val millisecondsSinceLocation = currentTime - locationTime

        return millisecondsSinceLocation / 1000L
    }

    private fun locationMatchesMinimumRequirements(location: Location): Boolean {
       return location.accuracy <= MIN_ACCURACY_METER && getSecondsSinceLocation(location) <= MAX_AGE_SECONDS
    }


    /**
     * Request location updates to get the current location.
     *
     * @param locationRequester: Abstract class implementation that contains a callback method that is called when a matching location was found
     * @param timeoutMillis: After the timeout the last location will be returned no matter if it matches the requirements or not
     * @return the last known location if this already satisfies our requirements
     */
    @SuppressLint("InlinedApi") // Suppressed, because we use a custom version provider which is injectable for testing
    open fun lastKnownOrRequestLocationUpdates(locationRequester: LocationRequester, timeoutMillis: Long?): Location? {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val lastLocation = getLastLocation()
        if (lastLocation != null && locationMatchesMinimumRequirements(lastLocation)) {
            return lastLocation
        }

        this.locationRequesters.add(locationRequester)

        // The fused location provider does not work reliably with Samsung + Android 12
        // We just stay with the legacy location, because this just works
        requestLocationUpdatesFromAnyProvider()

        if (timeoutMillis != null) {
            setTimeoutForLocationUpdate(requester =  locationRequester, timeoutMillis= timeoutMillis)
        }

        return null
    }

    /**
     * Set a timeout for location requests. After the timeout the last location will be returned no
     * matter if the location matches the requirements or not
     *
     * @param requester abstract class that contains a callback that is called when the timeout is reached
     * @param timeoutMillis milliseconds after which the timeout will be executed
     */
    private fun setTimeoutForLocationUpdate(requester: LocationRequester, timeoutMillis: Long) {
        val handler = Handler(Looper.getMainLooper())

        val runnable = kotlinx.coroutines.Runnable {
            if (this@LocationProvider.locationRequesters.size == 0) {
                // The location was already returned
                return@Runnable
            }

            Timber.d("Location request timed out")
            val lastLocation = this@LocationProvider.getLastLocation(checkRequirements = false)
            lastLocation?.let {
                requester.receivedAccurateLocationUpdate(location = lastLocation)
            }
            if (this@LocationProvider.locationRequesters.size == 1) {
                this@LocationProvider.stopLocationUpdates()
                this@LocationProvider.locationRequesters.clear()
            }else {
                this@LocationProvider.locationRequesters.remove(requester)
            }
        }

        handler.postDelayed(runnable, timeoutMillis)
        Timber.d("Location request timeout set to $timeoutMillis")
    }


    private fun requestLocationUpdatesFromAnyProvider() {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        Timber.d("Requesting location updates")
        val gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkProviderEnabled =
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.FUSED_PROVIDER,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                this,
                handler.looper
            )
        }
        
        if (networkProviderEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                this,
                handler.looper
            )
        }

        if (gpsProviderEnabled) {
            // Using GPS and Network provider, because the GPS provider does notwork indoors (it will never call the callback)
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                this,
                handler.looper
            )
        }

        if (!networkProviderEnabled && !gpsProviderEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                    // Error
                    Timber.e("ERROR: No location provider available")
                    stopLocationUpdates()
                    }
            }else {
                //Error
                Timber.e("ERROR: No location provider available")
                stopLocationUpdates()
            }
        }
    }

    fun stopLocationUpdates() {
        locationManager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        Timber.d("Location updated: ${location.latitude} ${location.longitude}")
        val bestLastLocation = this.bestLastLocation
        if (bestLastLocation == null) {
            this.bestLastLocation = location
        }else {
            if (bestLastLocation.time - location.time > MAX_AGE_SECONDS * 1000L) {
                // Current location is newer update
                this.bestLastLocation = location
            }else if (bestLastLocation.accuracy > location.accuracy) {
                this.bestLastLocation = location
            }
        }

        if (locationMatchesMinimumRequirements(location)) {
            stopLocationUpdates()
            this.bestLastLocation = location
            this.locationRequesters.forEach { locationRequester ->
                locationRequester.receivedAccurateLocationUpdate(location)
            }
            this.locationRequesters.clear()
        }else {
            Timber.d("New location does not satisfy requirements. Waiting for a better one")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    // Android Phones with SDK < 30 need these methods
    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    companion object {
        const val MIN_UPDATE_TIME_MS = 100L
        const val MIN_DISTANCE_METER = 0.0F
        const val MAX_AGE_SECONDS = 120L
        const val MIN_ACCURACY_METER = 120L
    }

}

abstract class LocationRequester {
    abstract fun receivedAccurateLocationUpdate(location: Location)
}