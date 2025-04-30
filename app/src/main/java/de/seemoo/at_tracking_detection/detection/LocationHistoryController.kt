package de.seemoo.at_tracking_detection.detection

import android.location.Location
import android.location.LocationListener
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.privacyPrint
import java.util.Date

object LocationHistoryController: LocationListener {

    private var locationHistory: ArrayList<Location> = ArrayList()
    private var listeners: HashSet<LocationHistoryListener> = HashSet()

    val lastLocation: Location?
        get() = locationHistory.lastOrNull()

    val lastLocationUpdate: Date?
        get() = locationHistory.lastOrNull()?.time?.let { Date(it) }

    val history: List<Location>
        get() = locationHistory

    override fun onLocationChanged(location: Location) {
        Utility.LocationLogger.log("Location changed to ${location.privacyPrint()} from ${location.provider}")
        locationHistory.add(location)
        listeners.forEach { it.receivedNewLocation(location) }
    }

    override fun onFlushComplete(requestCode: Int) {
        Utility.LocationLogger.log("Flush completed. Request code ${requestCode}")
    }

    override fun onProviderDisabled(provider: String) {
        Utility.LocationLogger.log("${provider} provider was disabled")
    }

    override fun onProviderEnabled(provider: String) {
        Utility.LocationLogger.log("${provider} provider was enabled")
    }

    /**
     * Removes locations that are older than 1 hour
     */
    fun cleanUpHistory() {
        // Remove old locations
        locationHistory =
            ArrayList(locationHistory.filter {
                (Date().time - it.time) < 60 * 60 * 1000
            })

        listeners.forEach { it.locationHistoryChanged(this, locationHistory) }
    }


    /**
     * Add a listener that is informed when a new location is available.
     * The listener receives calls when a new location was added and when the historu changes.
     * @param listener: The listener that should be added.
     */
    fun listenToLocationChanges(listener: LocationHistoryListener) {
        listeners.add(listener)
        listener.locationHistoryChanged(this, locationHistory)
        locationHistory.lastOrNull()?.let { listener.receivedNewLocation(it) }
    }

    /**
     * Remove the listener from the queue of listeners
     * @param listener: The listener that should be removed
     * @return true if the listener was removed from the list. False if it was not in the list
     */
    fun removeListener(listener: LocationHistoryListener):Boolean {
        return listeners.remove(listener)
    }


}

interface LocationHistoryListener {
    fun locationHistoryChanged(historyController: LocationHistoryController, history: ArrayList<Location>)
    fun receivedNewLocation(location: Location)
}