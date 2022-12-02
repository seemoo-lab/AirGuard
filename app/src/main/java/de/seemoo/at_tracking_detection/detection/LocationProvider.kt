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

        val bestLocation = bestLastLocation
        if (bestLocation != null && locationMatchesMinimumRequirements(bestLocation)) {
            return bestLocation
        }

        // The fused location provider does not work reliably with Samsung + Android 12
        // We just stay with the legacy location, because this just works
        return legacyGetLastLocationFromAnyProvider()
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
                    // Only GPS satisfies the requirements. Return it
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
     * @return the last known location if this already satisfies our requirements
     */
    @SuppressLint("InlinedApi") // Suppressed, because we use a custom version provider which is injectable for testing
    open fun lastKnownOrRequestLocationUpdates(locationRequester: LocationRequester): Location? {
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
        requestLegacyLocationUpdatesFromAnyProvider()

        return null
    }

    private fun requestLegacyLocationUpdatesFromAnyProvider() {
        if (ContextCompat.checkSelfPermission(ATTrackingDetectionApplication.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        Timber.d("Requesting location updates")
        val gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkProviderEnabled =
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

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
        val bestLastLocation = this.bestLastLocation?.let { it }
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

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    // Android Phones with SDK < 30 need these methods
    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    companion object {
        const val MIN_UPDATE_TIME_MS = 100L
        const val MIN_DISTANCE_METER = 0.0F
        const val MAX_AGE_SECONDS = 120L
        const val MIN_ACCURACY_METER = 120L
        const val MAX_LOCATION_DURATION = 60_000L /// Time until the location fetching will be stopped automatically
    }

}

public abstract class LocationRequester {
    public abstract fun receivedAccurateLocationUpdate(location: Location)
}