import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner
import kotlinx.coroutines.runBlocking

@SuppressLint("MissingPermission")
class PassiveLocationListener(private val context: Context) : LocationListener {

    // TODO: The Low Power Toggle from the system menu still has to be switched to using this

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    init {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0L, 0f, this)
        } else {
            // TODO: Handle missing permission
        }
    }

    override fun onLocationChanged(location: Location) {
        if (shouldTriggerScan(location)) {
            triggerScan(location)
        }
    }

    // TODO: check if this is necessary
    // override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun shouldTriggerScan(location: Location): Boolean {
        // TODO: Implement logic to check if the scan should be triggered
        return true
    }

    private fun triggerScan(location: Location) {
        runBlocking {
            BackgroundBluetoothScanner.scanInBackground(
                startedFrom = "PassiveLocationListener",
                useOnlyPassiveLocation = true,
                locationProvided = location
            )
        }
    }

    fun requestLocationUpdateIfNeeded() {
        val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        if (lastKnownLocation == null || isLocationUpdateTooOld(lastKnownLocation)) {
            locationManager.requestSingleUpdate(LocationManager.PASSIVE_PROVIDER, this, null)
        }
    }

    private fun isLocationUpdateTooOld(location: Location): Boolean {
        // TODO: Implement logic to check if the location update is too old
        return true
    }
}