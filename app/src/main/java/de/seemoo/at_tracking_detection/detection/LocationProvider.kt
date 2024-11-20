package de.seemoo.at_tracking_detection.detection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.util.Utility
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
open class LocationProvider @Inject constructor(
    private val locationManager: LocationManager
) : LocationListener {

    private val handler: Handler = Handler(Looper.getMainLooper())
    private var bestLastLocation: Location? = null
    private val locationRequesters = ArrayList<LocationRequester>()

    fun getLastLocation(checkRequirements: Boolean = true): Location? {
        if (ContextCompat.checkSelfPermission(
                ATTrackingDetectionApplication.getAppContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            Utility.LocationLogger.log("LocationProvider: Insufficient permissions")
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
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Utility.LocationLogger.log("LocationProvider: Insufficient permissions")
            return null
        }

        val bestLocation = bestLastLocation
        if (bestLocation != null && locationMatchesMinimumRequirements(bestLocation)) {
            Utility.LocationLogger.log("LocationProvider: return best location (checkRequirements: $checkRequirements): ${bestLocation.latitude}, Longitude: ${bestLocation.longitude}, Altitude: ${bestLocation.altitude}, Accuracy: ${bestLocation.accuracy}")
            return bestLocation
        }

        val lastLocation = legacyGetLastLocationFromAnyProvider(checkRequirements)

        if (lastLocation != null && bestLocation != null && !checkRequirements) {
            return if (lastLocation.time > bestLocation.time) {
                Utility.LocationLogger.log("LocationProvider: return last location (checkRequirements: $checkRequirements): ${lastLocation.latitude}, Longitude: ${lastLocation.longitude}, Altitude: ${lastLocation.altitude}, Accuracy: ${lastLocation.accuracy}")
                lastLocation
            } else {
                Utility.LocationLogger.log("LocationProvider: return best location (checkRequirements: $checkRequirements): ${bestLocation.latitude}, Longitude: ${bestLocation.longitude}, Altitude: ${bestLocation.altitude}, Accuracy: ${bestLocation.accuracy}")
                bestLocation
            }
        }

        if (lastLocation != null) {
            Utility.LocationLogger.log("LocationProvider: return last location (checkRequirements: $checkRequirements): ${lastLocation.latitude}, Longitude: ${lastLocation.longitude}, Altitude: ${lastLocation.altitude}, Accuracy: ${lastLocation.accuracy}")
        } else {
            Utility.LocationLogger.log("LocationProvider: return null (checkRequirements: $checkRequirements)")
        }
        return lastLocation
    }

    private fun legacyGetLastLocationFromAnyProvider(checkRequirements: Boolean): Location? {
        // Check for location permission
        if (ContextCompat.checkSelfPermission(
                ATTrackingDetectionApplication.getAppContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Utility.LocationLogger.log("LocationProvider: Insufficient permissions")
            return null
        }

        // Get the last known locations from both providers
        Utility.LocationLogger.log("LocationProvider: Request last known location from network and gps provider")
        val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (networkLocation != null) {
            Utility.LocationLogger.log("LocationProvider: Got network location: Latitude: ${networkLocation.latitude}, Longitude: ${networkLocation.longitude}, Altitude: ${networkLocation.altitude}, Accuracy: ${networkLocation.accuracy}")
        } else {
            Utility.LocationLogger.log("LocationProvider: Network location is null")
        }
        val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (gpsLocation != null) {
            Utility.LocationLogger.log("LocationProvider: Got gps location: Latitude: ${gpsLocation.latitude}, Longitude: ${gpsLocation.longitude}, Altitude: ${gpsLocation.altitude}, Accuracy: ${gpsLocation.accuracy}")
        } else {
            Utility.LocationLogger.log("LocationProvider: GPS location is null")
        }

        // If both locations are available, return the one that is more current and meets the minimum requirements
        if (networkLocation != null && gpsLocation != null) {
            val bestLocation = if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
            if (locationMatchesMinimumRequirements(bestLocation)) {
                Utility.LocationLogger.log("LocationProvider: Both network and gps location available, return best location: ${bestLocation.latitude}, Longitude: ${bestLocation.longitude}, Altitude: ${bestLocation.altitude}, Accuracy: ${bestLocation.accuracy}")
                return bestLocation
            }
        }

        // If only one location is available, return it if it meets the minimum requirements
        if (networkLocation != null && locationMatchesMinimumRequirements(networkLocation)) {
            Utility.LocationLogger.log("LocationProvider: only network location meets requirements: ${networkLocation.latitude}, Longitude: ${networkLocation.longitude}, Altitude: ${networkLocation.altitude}, Accuracy: ${networkLocation.accuracy}")
            return networkLocation
        }
        if (gpsLocation != null && locationMatchesMinimumRequirements(gpsLocation)) {
            Utility.LocationLogger.log("LocationProvider: only gps location meets requirements: ${gpsLocation.latitude}, Longitude: ${gpsLocation.longitude}, Altitude: ${gpsLocation.altitude}, Accuracy: ${gpsLocation.accuracy}")
            return gpsLocation
        }

        // If neither location meets the minimum requirements, return null
        if (checkRequirements) {
            Utility.LocationLogger.log("LocationProvider: Neither network nor gps meets requirements, return null")
            return null
        }

        // If no location requirements are specified, return the last known location from either provider, or null if none are available
        Utility.LocationLogger.log("LocationProvider: Neither network nor gps meets requirements, return last known location")
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
            } else {
                Timber.d("Location too old")
            }
        } else {
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
            Utility.LocationLogger.log("LocationProvider: Insufficient permissions")
            return null
        }

        // Get the last known location
        val lastLocation = getLastLocation()

        // If the last location is available and meets the minimum requirements, return it
        if (lastLocation != null && locationMatchesMinimumRequirements(lastLocation)) {
            Utility.LocationLogger.log("LocationProvider: Last known location meets requirements, return last location: ${lastLocation.latitude}, Longitude: ${lastLocation.longitude}, Altitude: ${lastLocation.altitude}, Accuracy: ${lastLocation.accuracy}")
            return lastLocation
        }

        // Add the location requester to the list of active requesters
        Utility.LocationLogger.log("LocationProvider: Requesting location updates")
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
            Utility.LocationLogger.log("LocationProvider: Insufficient permissions")
            return
        }

        // Get the list of enabled location providers
        val enabledProviders = locationManager.allProviders
            .filter { locationManager.isProviderEnabled(it) }

        Utility.LocationLogger.log("LocationProvider: These providers are enabled, Requesting location updates from $enabledProviders")

        // Request location updates from all enabled providers
        enabledProviders.forEach {
            Utility.LocationLogger.log("LocationProvider: Requesting location updates from $it")
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
            Utility.LocationLogger.log("LocationProvider: No location provider available, stopping location updates")
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
        } else {
            if (bestLastLocation.time - location.time > MAX_AGE_SECONDS * 1000L) {
                // Current location is newer update
                this.bestLastLocation = location
            } else if (bestLastLocation.accuracy > location.accuracy) {
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
        } else {
            Timber.d("New location does not satisfy requirements. Waiting for a better one")
        }
    }

    // Android Phones with SDK < 30 need these methods

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // This method is deprecated, but required to avoid AbstractMethodError in API Level 29 and below
        Timber.d("Provider status changed: $provider, status: $status")
    }

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    companion object {
        const val MIN_UPDATE_TIME_MS = 100L
        const val MIN_DISTANCE_METER = 0.0F
        const val MAX_AGE_SECONDS = 120L
        const val MIN_ACCURACY_METER = 120L

        fun isLocationTurnedOn(): Boolean {
            val context = ATTrackingDetectionApplication.getAppContext()
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
                    LocationManager.FUSED_PROVIDER)
            } else {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }
        }
    }
}

abstract class LocationRequester {
    abstract fun receivedAccurateLocationUpdate(location: Location)
}