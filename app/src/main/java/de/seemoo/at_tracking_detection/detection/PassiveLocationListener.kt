import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator.Companion.MAX_AGE_OF_LOCATION
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator.Companion.PASSIVE_SCAN_TIME_BETWEEN_SCANS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset

@SuppressLint("MissingPermission")
class PassiveLocationListener(private val context: Context) : LocationListener {

    // TODO: This should work while the app is running, it is now necessary to deactivate the old approach while use low power mode is activated

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    init {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0L, 0f, this)
        } else {
            // Do nothing, the location updates will be requested only when the permission is granted
        }
    }

    override fun onLocationChanged(location: Location) {
        Timber.d("Passive location update received: ${location.latitude}, ${location.longitude}")
        if (shouldTriggerScan()) {
            Timber.d("Triggering scan based on passive location update")
            triggerScan(location)
        }
    }

    // TODO: check if this is necessary, onStatusChanged is Deprecated
    // override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun shouldTriggerScan(): Boolean {
        val scanRepository = ATTrackingDetectionApplication.getCurrentApp().scanRepository
        val lastScan = scanRepository.lastScan
        val lastScanEndDate = lastScan.endDate
        val currentTime = LocalDateTime.now()
        if (lastScanEndDate == null) {
            return true
        }
        val timeDifference = currentTime.toEpochSecond(ZoneOffset.UTC) - lastScanEndDate.toEpochSecond(ZoneOffset.UTC)
        return timeDifference > PASSIVE_SCAN_TIME_BETWEEN_SCANS
    }

    private fun triggerScan(location: Location) {
        Timber.d("Triggering background scan from PassiveLocationListener")
        CoroutineScope(Dispatchers.IO).launch {
            BackgroundBluetoothScanner.scanInBackground(
                startedFrom = "PassiveLocationListener",
                useOnlyPassiveLocation = true,
                locationProvided = location
            )
            Timber.d("PassiveLocationListener scan done")
        }
    }

    // This is currently not used, check in the future if we even need this
    fun requestLocationUpdateIfNeeded() {
        val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        if (lastKnownLocation == null || isLocationUpdateTooOld(lastKnownLocation)) {
            locationManager.requestSingleUpdate(LocationManager.PASSIVE_PROVIDER, this, null)
        }
    }

    private fun isLocationUpdateTooOld(location: Location): Boolean {
        val currentTime = System.currentTimeMillis()
        val locationTime = location.time
        val timeDifference = currentTime - locationTime
        return timeDifference > MAX_AGE_OF_LOCATION
    }
}