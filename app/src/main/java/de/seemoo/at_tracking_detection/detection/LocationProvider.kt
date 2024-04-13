package de.seemoo.at_tracking_detection.detection

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
open class LocationProvider @Inject constructor(
    private val locationManager: LocationManager): LocationListener {

    private val handler: Handler = Handler(Looper.getMainLooper())
    private var bestLastLocation: Location? = null

    private val locationRequesters = ArrayList<LocationRequester>()

    fun getLastLocation(checkRequirements: Boolean = true): Location? {
        if (ContextCompat.checkSelfPermission(
                ATTrackingDetectionApplication.getAppContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                ATTrackingDetectionApplication.getAppContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return getLastLocationFromAnyProvider(checkRequirements)
    }

    /**
     * Fetches the most recent location from network and gps and returns the one that has been received more recently
     * @return the most recent location across multiple providers
     */
    private fun getLastLocationFromAnyProvider(checkRequirements: Boolean): Location? {
        if (ContextCompat.checkSelfPermission(
                ATTrackingDetectionApplication.getAppContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                ATTrackingDetectionApplication.getAppContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
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
        // Check for location permission
        if (ContextCompat.checkSelfPermission(
                ATTrackingDetectionApplication.getAppContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                ATTrackingDetectionApplication.getAppContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        // Get the last known locations from both providers
        val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

        // If both locations are available, return the one that is more current and meets the minimum requirements
        if (networkLocation != null && gpsLocation != null) {
            val bestLocation = if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
            if (locationMatchesMinimumRequirements(bestLocation)) {
                return bestLocation
            }
        }

        // If only one location is available, return it if it meets the minimum requirements
        if (networkLocation != null && locationMatchesMinimumRequirements(networkLocation)) {
            return networkLocation
        }
        if (gpsLocation != null && locationMatchesMinimumRequirements(gpsLocation)) {
            return gpsLocation
        }

        // If neither location meets the minimum requirements, return null
        if (checkRequirements) {
            return null
        }

        // If no location requirements are specified, return the last known location from either provider, or null if none are available
        return networkLocation ?: gpsLocation
    }

    private fun getSecondsSinceLocation(location: Location): Long {
        val locationTime = location.time
        val currentTime = Date().time
        val millisecondsSinceLocation = currentTime - locationTime

        return millisecondsSinceLocation / 1000L
    }

    private fun locationMatchesMinimumRequirements(location: Location): Boolean {
        if (location.accuracy <= MIN_ACCURACY_METER) {
            if (getSecondsSinceLocation(location) <= MAX_AGE_SECONDS) {
                return true
            }else {
                Timber.d("Location too old")
            }
        }else {
            Timber.d("Location accuracy is not good enough")
        }
        return false
    }


    /**
     * Request location updates to get the current location.
     *
     * @param locationRequester: Abstract class implementation that contains a callback method that is called when a matching location was found
     * @param timeoutMillis: After the timeout the last location will be returned no matter if it matches the requirements or not
     * @return the last known location if this already satisfies our requirements
     */
    open fun lastKnownOrRequestLocationUpdates(
        locationRequester: LocationRequester,
        timeoutMillis: Long? = null
    ): Location? {
        // Check for location permission
        if (ContextCompat.checkSelfPermission(
                ATTrackingDetectionApplication.getAppContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        // Get the last known location
        val lastLocation = getLastLocation()

        // If the last location is available and meets the minimum requirements, return it
        if (lastLocation != null && locationMatchesMinimumRequirements(lastLocation)) {
            return lastLocation
        }

        // Add the location requester to the list of active requesters
        this.locationRequesters.add(locationRequester)

        // Request location updates from all enabled providers
        requestLocationUpdatesFromAnyProvider()

        // If a timeout is specified, set a timeout for the location update
        if (timeoutMillis != null) {
            setTimeoutForLocationUpdate(requester = locationRequester, timeoutMillis = timeoutMillis)
        }

        // Return null, since we don't have a location immediately available
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
        // Create a runnable to handle the timeout
        val runnable = Runnable {
            // If the location requester list is empty, the location has already been returned
            if (this@LocationProvider.locationRequesters.isEmpty()) {
                return@Runnable
            }

            // Log the timeout and get the last known location, regardless of whether it meets the requirements
            Timber.d("Location request timed out")
            val lastLocation = this@LocationProvider.getLastLocation(checkRequirements = false)

            // If the last location is available, notify the requester
            lastLocation?.let {
                requester.receivedAccurateLocationUpdate(location = it)
            }

            // If there is only one requester left, stop location updates and clear the list
            if (this@LocationProvider.locationRequesters.size == 1) {
                this@LocationProvider.stopLocationUpdates()
                this@LocationProvider.locationRequesters.clear()
            } else {
                // Otherwise, remove the requester from the list
                this@LocationProvider.locationRequesters.remove(requester)
            }
        }

        // Schedule the runnable to be executed after the timeout period
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(runnable, timeoutMillis)

        // Log the timeout settings
        Timber.d("Location request timeout set to $timeoutMillis")
    }

    private fun requestLocationUpdatesFromAnyProvider() {
        // Check for location permission
        if (ContextCompat.checkSelfPermission(
                ATTrackingDetectionApplication.getAppContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("Not requesting location, permission not granted")
            return
        }

        // Get the list of enabled location providers
        val enabledProviders = locationManager.allProviders
            .filter { locationManager.isProviderEnabled(it) }

        // Request location updates from all enabled providers
        enabledProviders.forEach {
            locationManager.requestLocationUpdates(
                it,
                MIN_UPDATE_TIME_MS,
                MIN_DISTANCE_METER,
                this,
                handler.looper
            )
        }

        Timber.i("Requesting location updates from $enabledProviders")

        // If no location providers are enabled, log an error and stop location updates
        if (enabledProviders.isEmpty()) {
            Timber.e("ERROR: No location provider available")
            stopLocationUpdates()
        }
    }

    private fun stopLocationUpdates() {
        locationManager.removeUpdates(this)
        Timber.i("Stopping location updates")
    }

    override fun onLocationChanged(location: Location) {
        Timber.d("Location updated: ${location.latitude} ${location.longitude}, accuracy: ${location.accuracy}, date: ${Date(location.time)}")
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