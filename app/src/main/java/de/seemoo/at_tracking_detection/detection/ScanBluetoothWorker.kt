package de.seemoo.at_tracking_detection.detection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.database.models.device.*
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getPublicKey
import de.seemoo.at_tracking_detection.database.models.Location as LocationModel
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.ble.BLEScanCallback
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import de.seemoo.at_tracking_detection.detection.TrackingDetectorWorker.Companion.getLocation
import kotlinx.coroutines.delay
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@HiltWorker
class ScanBluetoothWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scanRepository: ScanRepository,
    private val locationProvider: LocationProvider,
    private val notificationService: NotificationService,
    var backgroundWorkScheduler: BackgroundWorkScheduler
) :
    CoroutineWorker(appContext, workerParams) {

    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var scanResultDictionary: ConcurrentHashMap<String, DiscoveredDevice> = ConcurrentHashMap()

    var location: Location? = null
        set(value) {
            field = value
            if (value != null) {
                locationRetrievedCallback?.let { it() }
            }
        }

    private var locationRetrievedCallback: (() -> Unit)? = null

    private var locationFetchStarted: Long? = null

    override suspend fun doWork(): Result {
        Timber.d("Bluetooth scanning worker started!")
        val scanMode = getScanMode()
        val scanId = scanRepository.insert(Scan(startDate = LocalDateTime.now(), isManual = false, scanMode = scanMode))

        if (!Utility.checkBluetoothPermission()) {
            Timber.d("Permission to perform bluetooth scan missing")
            return Result.retry()
        }
        try {
            val bluetoothManager =
                applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter
        } catch (e: Throwable) {
            Timber.e("BluetoothAdapter not found!")
            return Result.retry()
        }

        scanResultDictionary = ConcurrentHashMap()

        val useLocation = SharedPrefs.useLocationInTrackingDetection
        if (useLocation) {
            // Returns the last known location if this matches our requirements or starts new location updates
            locationFetchStarted = System.currentTimeMillis()
            location = locationProvider.lastKnownOrRequestLocationUpdates(locationRequester =  locationRequester, timeoutMillis = LOCATION_UPDATE_MAX_TIME_MS - 2000L)
        }

        //Starting BLE Scan
        Timber.d("Start Scanning for bluetooth le devices...")
        val scanSettings =
            ScanSettings.Builder().setScanMode(scanMode).build()

        SharedPrefs.isScanningInBackground = true
        BLEScanCallback.startScanning(bluetoothAdapter.bluetoothLeScanner, DeviceManager.scanFilter, scanSettings, leScanCallback)

        val scanDuration: Long = getScanDuration()
        delay(scanDuration)
        BLEScanCallback.stopScanning(bluetoothAdapter.bluetoothLeScanner)
        Timber.d("Scanning for bluetooth le devices stopped!. Discovered ${scanResultDictionary.size} devices")

        //Waiting for updated location to come in
        val fetchedLocation = waitForRequestedLocation()
        Timber.d("Fetched location? $fetchedLocation")
        if (location == null) {
            // Get the last location no matter if the requirements match or not
            location = locationProvider.getLastLocation(checkRequirements = false)
        }

        val validDeviceTypes = DeviceType.getAllowedDeviceTypesFromSettings()

        //Adding all scan results to the database after the scan has finished
        scanResultDictionary.forEach { (_, discoveredDevice) ->
            val deviceType = DeviceManager.getDeviceType(discoveredDevice.scanResult)

            if (deviceType in validDeviceTypes) {
                insertScanResult(
                    discoveredDevice.scanResult,
                    location?.latitude,
                    location?.longitude,
                    location?.accuracy,
                    discoveredDevice.discoveryDate,
                )
            }
        }

        SharedPrefs.lastScanDate = LocalDateTime.now()
        SharedPrefs.isScanningInBackground = false
        val scan = scanRepository.scanWithId(scanId.toInt())
        if (scan != null) {
            scan.endDate = LocalDateTime.now()
            scan.duration = scanDuration.toInt() / 1000
            scan.noDevicesFound = scanResultDictionary.size
            scanRepository.update(scan)
        }

        Timber.d("Scheduling tracking detector worker")
        backgroundWorkScheduler.scheduleTrackingDetector()
        BackgroundWorkScheduler.scheduleAlarmWakeupIfScansFail()

        return Result.success(
            Data.Builder()
                .putLong("duration", scanDuration)
                .putInt("mode", scanMode)
                .putInt("devicesFound", scanResultDictionary.size)
                .build()
        )
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scanResult: ScanResult) {
            super.onScanResult(callbackType, scanResult)
            //Checks if the device has been found already
            if (!scanResultDictionary.containsKey(getPublicKey(scanResult))) {
                Timber.d("Found ${scanResult.device.address} at ${LocalDateTime.now()}")
                scanResultDictionary[getPublicKey(scanResult)] =
                    DiscoveredDevice(scanResult, LocalDateTime.now())
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Timber.e("Bluetooth scan failed $errorCode")
            if (BuildConfig.DEBUG) {
                notificationService.sendBLEErrorNotification()
            }
        }
    }

    private val locationRequester: LocationRequester = object : LocationRequester() {
        override fun receivedAccurateLocationUpdate(location: Location) {
            val started = locationFetchStarted ?: System.currentTimeMillis()
            Timber.d("Got location in ${(System.currentTimeMillis()-started)/1000}s")
            this@ScanBluetoothWorker.location = location
            this@ScanBluetoothWorker.locationRetrievedCallback?.let { it() }
        }
    }

    private fun getScanMode(): Int {
        val useLowPower = SharedPrefs.useLowPowerBLEScan
        return if (useLowPower) {
            ScanSettings.SCAN_MODE_LOW_POWER
        } else {
            ScanSettings.SCAN_MODE_LOW_LATENCY
        }
    }

    private fun getScanDuration(): Long {
        val useLowPower = SharedPrefs.useLowPowerBLEScan
        return if (useLowPower) {
            30_000L
        } else {
            20_000L
        }
    }

    private suspend fun waitForRequestedLocation(): Boolean {
        if (location != null || !SharedPrefs.useLocationInTrackingDetection) {
            //Location already there. Just return
            return true
        }

        return suspendCoroutine { cont ->
            var coroutineFinished = false

            val handler = Handler(Looper.getMainLooper())
            val runnable = Runnable {
                if (!coroutineFinished) {
                    coroutineFinished = true
                    locationRetrievedCallback = null
                    Timber.d("Could not get location update in time.")
                    cont.resume(false)
                }
            }

            locationRetrievedCallback = {
                if (!coroutineFinished) {
                    handler.removeCallbacks(runnable)
                    coroutineFinished = true
                    cont.resume(true)
                }
            }

            // Fallback if no location is fetched in time
            val maximumLocationDurationMillis = LOCATION_UPDATE_MAX_TIME_MS
            handler.postDelayed(runnable, maximumLocationDurationMillis)
        }
    }

    class DiscoveredDevice(var scanResult: ScanResult, var discoveryDate: LocalDateTime)

    companion object {
        const val MAX_DISTANCE_UNTIL_NEW_LOCATION: Float = 150f // in meters
        const val TIME_BETWEEN_BEACONS: Long = 15 // 15 minutes until the same beacon gets saved again in the db
        const val LOCATION_UPDATE_MAX_TIME_MS: Long = 122_000L // Wait maximum 122s to get a location update

        suspend fun insertScanResult(
            scanResult: ScanResult,
            latitude: Double?,
            longitude: Double?,
            accuracy: Float?,
            discoveryDate: LocalDateTime,
        ): Pair<BaseDevice?, Beacon?> {
            val deviceSaved = saveDevice(scanResult, discoveryDate) ?: return Pair(null, null) // return when device does not qualify to be saved

            // set locationId to null if gps location could not be retrieved
            val locId: Int? = saveLocation(latitude, longitude, discoveryDate, accuracy)?.locationId

            val beaconSaved = saveBeacon(scanResult, discoveryDate, locId) ?: return Pair(null, null)

            return Pair(deviceSaved, beaconSaved)
        }

    private suspend fun saveBeacon(
            scanResult: ScanResult,
            discoveryDate: LocalDateTime,
            locId: Int?
        ): Beacon? {
            val beaconRepository = ATTrackingDetectionApplication.getCurrentApp()?.beaconRepository ?: return null
            val uuids = scanResult.scanRecord?.serviceUuids?.map { it.toString() }?.toList()
            val uniqueIdentifier = getPublicKey(scanResult)

            val connectionState: ConnectionState = BaseDevice.getConnectionState(scanResult)
            val connectionStateString = Utility.connectionStateToString(connectionState)

            var beacon: Beacon? = null
            val beacons = beaconRepository.getDeviceBeaconsSince(
                deviceAddress = uniqueIdentifier,
                since = discoveryDate.minusMinutes(TIME_BETWEEN_BEACONS)
            ) // sorted by newest first

            if (beacons.isEmpty()) {
                Timber.d("Add new Beacon to the database!")
                beacon = if (BuildConfig.DEBUG) {
                    // Save the manufacturer data to the beacon
                    Beacon(
                        discoveryDate, scanResult.rssi, getPublicKey(scanResult), locId,
                        scanResult.scanRecord?.bytes, uuids, connectionStateString
                    )
                } else {
                    Beacon(
                        discoveryDate, scanResult.rssi, getPublicKey(scanResult), locId,
                        null, uuids, connectionStateString
                    )
                }
                beaconRepository.insert(beacon)
            } else if (beacons[0].locationId == null && locId != null && locId != 0){
                // Update beacon within the last TIME_BETWEEN_BEACONS minutes with location
                Timber.d("Beacon already in the database... Adding Location")
                beacon = beacons[0]
                beacon.locationId = locId
                if (beacon.connectionState == "UNKNOWN" && connectionState != ConnectionState.UNKNOWN) {
                    beacon.connectionState = connectionStateString
                }
                beaconRepository.update(beacon)
            }

            Timber.d("Beacon: $beacon")

            return beacon
        }

        private suspend fun saveDevice(
            scanResult: ScanResult,
            discoveryDate: LocalDateTime
        ): BaseDevice? {
            val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository ?: return null

            val deviceAddress = getPublicKey(scanResult)

            // Checks if Device already exists in device database
            var device = deviceRepository.getDevice(deviceAddress)
            if (device == null) {
                // Do not Save Samsung Devices
                device = BaseDevice(scanResult)

                // Check if ConnectionState qualifies Device to be saved
                // Only Save when Device is offline long enough
                if (BaseDevice.getConnectionState(scanResult) !in DeviceManager.savedConnectionStates) {
                    Timber.d("Device not in a saved connection state... Skipping!")
                    return null
                }

                if (BaseDevice.getConnectionState(scanResult) !in DeviceManager.unsafeConnectionState) {
                    Timber.d("Device is safe and will be hidden to the user!")
                    device.safeTracker = true
                }

                Timber.d("Add new Device to the database!")
                deviceRepository.insert(device)
            } else {
                Timber.d("Device already in the database... Updating the last seen date!")
                device.lastSeen = discoveryDate
                deviceRepository.update(device)
            }

            Timber.d("Device: $device")
            return device
        }

        private suspend fun saveLocation(
            latitude: Double?,
            longitude: Double?,
            discoveryDate: LocalDateTime,
            accuracy: Float?
        ): LocationModel? {
            val locationRepository = ATTrackingDetectionApplication.getCurrentApp()?.locationRepository ?: return null

            // set location to null if gps location could not be retrieved
            var location: LocationModel? = null

            if (latitude != null && longitude != null) {
                // Get closest location from database
                location = locationRepository.closestLocation(latitude, longitude)

                var distanceBetweenLocations: Float = Float.MAX_VALUE

                if (location != null) {
                    val locationA = getLocation(latitude, longitude)
                    val locationB = getLocation(location.latitude, location.longitude)
                    distanceBetweenLocations = locationA.distanceTo(locationB)
                }

                if (location == null || distanceBetweenLocations > MAX_DISTANCE_UNTIL_NEW_LOCATION) {
                    // Create new location entry
                    Timber.d("Add new Location to the database!")
                    location = LocationModel(discoveryDate, longitude, latitude, accuracy)
                    locationRepository.insert(location)
                } else {
                    // If location is within the set limit, just use that location and update lastSeen
                    Timber.d("Location already in the database... Updating the last seen date!")
                    location.lastSeen = discoveryDate
                    locationRepository.update(location)

                }

                Timber.d("Location: $location")
            }
            return location
        }
    }
}
