package de.seemoo.at_tracking_detection.detection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Location
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object BackgroundBluetoothScanner {
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var scanResultDictionary: ConcurrentHashMap<String, DiscoveredDevice> = ConcurrentHashMap()

    private var applicationContext: Context = ATTrackingDetectionApplication.getAppContext()

    private val executor: ExecutorService = Executors.newFixedThreadPool(1)

    private lateinit var bluetoothLeScanner: BluetoothLeScanner

    var location: android.location.Location? = null
        set(value) {
            field = value
            if (value != null) {
                locationRetrievedCallback?.let { it() }
            }
        }

    private var locationRetrievedCallback: (() -> Unit)? = null

    private var locationFetchStarted: Long? = null

    val backgroundWorkScheduler: BackgroundWorkScheduler
        get() {
            return ATTrackingDetectionApplication.getCurrentApp().backgroundWorkScheduler
        }

    val notificationService: NotificationService
        get() {
        return ATTrackingDetectionApplication.getCurrentApp().notificationService
    }
    private val locationProvider: LocationProvider
        get() {
            return ATTrackingDetectionApplication.getCurrentApp().locationProvider
        }

    /*
    Starts the background scan. True means the start was successful
     */
    fun startScanInBackground(startedFrom: String): Boolean {
        if (!executor.isShutdown && !executor.isTerminated) {
            Timber.w("BackgroundBluetoothScanner scan already running")
            return false
        }

        Timber.d("Starting BackgroundBluetoothScanner from $startedFrom")

        val scanMode = ScanSettings.SCAN_MODE_LOW_POWER

        if (!Utility.checkBluetoothPermission()) {
            Timber.d("Permission to perform bluetooth scan is missing")
            return false
        }

        try {
            val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter
            bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
            if (!bluetoothAdapter.isEnabled || bluetoothAdapter.bluetoothLeScanner == null) {
                Timber.e("Bluetooth is disabled or BLE is not supported on this device.")
                return false
            }
        } catch (e: Throwable) {
            Timber.e("BluetoothAdapter not found or BLE not supported!")
            return false
        }

        // Starting BLE Scan
        Timber.d("Start Scanning for bluetooth le devices...")

        // set scan Settings (e.g. Low Power Mode)
        val scanSettings = ScanSettings.Builder().setScanMode(scanMode).build()
        SharedPrefs.isScanningInBackground = true
        val filters = DeviceManager.scanFilter

        executor.execute {
            bluetoothLeScanner.startScan(filters, scanSettings, leScanCallback)
        }

        // TODO: how to handle this?
        Timber.d("Scheduling tracking detector worker")

        // TODO backgroundWorkScheduler.scheduleTrackingDetector()
        // TODO BackgroundWorkScheduler.scheduleAlarmWakeupIfScansFail()
        return true
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scanResult: ScanResult) {
            super.onScanResult(callbackType, scanResult)
            val wrappedScanResult = ScanResultWrapper(scanResult)

            val processFurther: Boolean = addScanResultToDictionary(wrappedScanResult)
            val scope = CoroutineScope(Dispatchers.Default)

            if (processFurther) {
                if (wrappedScanResult.connectionState !in DeviceManager.savedConnectionStates) {
                    Timber.d("Device not in a saved connection state... Skipping!")
                    return
                }
                scope.launch {
                    val useLocation = SharedPrefs.useLocationInTrackingDetection
                    if (useLocation) {
                        // Returns the last known location if this matches our requirements or starts new location updates
                        locationFetchStarted = System.currentTimeMillis()
                        location = locationProvider.lastKnownOrRequestLocationUpdates(locationRequester =  locationRequester, timeoutMillis = LOCATION_UPDATE_MAX_TIME_MS - 2000L)
                        if (location == null) {
                            Timber.e("Failed to retrieve location")
                        }
                    }

                    Timber.d("Waiting for location update")
                    val fetchedLocation = waitForRequestedLocation()
                    Timber.d("Fetched location? $fetchedLocation")
                    if (location == null) {
                        // Get the last location no matter if the requirements match or not
                        location = locationProvider.getLastLocation(checkRequirements = false)
                    }

                    val validDeviceTypes = DeviceType.getAllowedDeviceTypesFromSettings()
                    val deviceType = wrappedScanResult.deviceType

                    if (deviceType in validDeviceTypes) {
                        insertScanResult(
                            wrappedScanResult = wrappedScanResult,
                            latitude = location?.latitude,
                            longitude = location?.longitude,
                            altitude = location?.altitude,
                            accuracy = location?.accuracy,
                            discoveryDate = LocalDateTime.now(),
                        )
                    }
                }
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
        override fun receivedAccurateLocationUpdate(location: android.location.Location) {
            val started = locationFetchStarted ?: System.currentTimeMillis()
            Timber.d("Got location in ${(System.currentTimeMillis()-started)/1000}s")
            this@BackgroundBluetoothScanner.location = location
            this@BackgroundBluetoothScanner.locationRetrievedCallback?.let { it() }
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

    class DiscoveredDevice(var discoveryDate: LocalDateTime, var lastSeenDate: LocalDateTime)

    const val MAX_DISTANCE_UNTIL_NEW_LOCATION: Float = 150f // in meters
    const val TIME_BETWEEN_BEACONS: Long = 15 // 15 minutes until the same beacon gets saved again in the db
    private const val LOCATION_UPDATE_MAX_TIME_MS: Long = 122_000L // Wait maximum 122s to get a location update

    /*
    Adds the Scan Result to the scanResultDictionary, if return true than that means the device should be saved and processed further
     */
    private fun addScanResultToDictionary(wrappedScanResult: ScanResultWrapper): Boolean {
        if (!scanResultDictionary.containsKey(wrappedScanResult.uniqueIdentifier)) {
            Timber.d("Found ${wrappedScanResult.uniqueIdentifier} at ${LocalDateTime.now()}")
            scanResultDictionary[wrappedScanResult.uniqueIdentifier] =
                DiscoveredDevice(LocalDateTime.now(), LocalDateTime.now())
            return true
        } else if (scanResultDictionary[wrappedScanResult.uniqueIdentifier]?.lastSeenDate != null) {
            val lastSeenDate = scanResultDictionary[wrappedScanResult.uniqueIdentifier]?.lastSeenDate!!
            val discoveryDate = scanResultDictionary[wrappedScanResult.uniqueIdentifier]?.discoveryDate!!
            if (lastSeenDate.plusMinutes(TIME_BETWEEN_BEACONS).isBefore(LocalDateTime.now())) {
                Timber.d("Found ${wrappedScanResult.uniqueIdentifier} again at ${LocalDateTime.now()}")
                scanResultDictionary[wrappedScanResult.uniqueIdentifier] =
                    DiscoveredDevice(discoveryDate, LocalDateTime.now())
                return true
            }
        }
        return false
    }

    suspend fun insertScanResult(
        wrappedScanResult: ScanResultWrapper,
        latitude: Double?,
        longitude: Double?,
        altitude: Double?,
        accuracy: Float?,
        discoveryDate: LocalDateTime,
    ): Pair<BaseDevice?, Beacon?> {
        if (altitude != null && altitude > TrackingDetectorConstants.IGNORE_DEVICE_ABOVE_ALTITUDE) {
            Timber.d("Ignoring device for locations above ${TrackingDetectorConstants.IGNORE_DEVICE_ABOVE_ALTITUDE}m, we assume the User is on a plane!")
            // Do not save device at all in case we assume it is on a plane
            return Pair(null, null)
        }

        val deviceSaved = saveDevice(wrappedScanResult, discoveryDate) ?: return Pair(
            null,
            null
        ) // return when device does not qualify to be saved

        // set locationId to null if gps location could not be retrieved
        val locId: Int? = saveLocation(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            discoveryDate = discoveryDate,
            accuracy = accuracy
        )?.locationId

        val beaconSaved =
            saveBeacon(wrappedScanResult, discoveryDate, locId) ?: return Pair(null, null)

        return Pair(deviceSaved, beaconSaved)
    }

    private suspend fun saveBeacon(
        wrappedScanResult: ScanResultWrapper,
        discoveryDate: LocalDateTime,
        locId: Int?
    ): Beacon? {
        val beaconRepository =
            ATTrackingDetectionApplication.getCurrentApp().beaconRepository
        val uuids = wrappedScanResult.serviceUuids
        val uniqueIdentifier = wrappedScanResult.uniqueIdentifier

        val connectionState: ConnectionState = wrappedScanResult.connectionState
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
                    discoveryDate, wrappedScanResult.rssiValue, wrappedScanResult.uniqueIdentifier, locId,
                    wrappedScanResult.mfg, uuids, connectionStateString
                )
            } else {
                Beacon(
                    discoveryDate, wrappedScanResult.rssiValue, wrappedScanResult.uniqueIdentifier, locId,
                    null, uuids, connectionStateString
                )
            }
            beaconRepository.insert(beacon)
        } else if (beacons[0].locationId == null && locId != null && locId != 0) {
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
        wrappedScanResult: ScanResultWrapper,
        discoveryDate: LocalDateTime
    ): BaseDevice? {
        val deviceRepository =
            ATTrackingDetectionApplication.getCurrentApp().deviceRepository

        val deviceAddress = wrappedScanResult.uniqueIdentifier

        // Checks if Device already exists in device database
        var device = deviceRepository.getDevice(deviceAddress)
        if (device == null) {
            // Do not Save Samsung Devices
            device = BaseDevice(wrappedScanResult.scanResult)

            // Check if ConnectionState qualifies Device to be saved
            // Only Save when Device is offline long enough
            if (wrappedScanResult.connectionState !in DeviceManager.savedConnectionStates) {
                Timber.d("Device not in a saved connection state... Skipping!")
                return null
            }

            if (wrappedScanResult.connectionState !in DeviceManager.unsafeConnectionState) {
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
        altitude: Double?,
        discoveryDate: LocalDateTime,
        accuracy: Float?
    ): Location? {
        if (altitude != null && altitude > TrackingDetectorConstants.IGNORE_LOCATION_ABOVE_ALTITUDE) {
            Timber.d("Ignoring location above ${TrackingDetectorConstants.IGNORE_LOCATION_ABOVE_ALTITUDE}m, we assume the User might be on a plane!")
            // Do not save location object
            return null
        }

        val locationRepository =
            ATTrackingDetectionApplication.getCurrentApp().locationRepository

        // set location to null if gps location could not be retrieved
        var location: Location? = null

        if (latitude != null && longitude != null) {
            // Get closest location from database
            location = locationRepository.closestLocation(latitude, longitude)

            var distanceBetweenLocations: Float = Float.MAX_VALUE

            if (location != null) {
                val locationA = TrackingDetectorWorker.getLocation(latitude, longitude)
                val locationB =
                    TrackingDetectorWorker.getLocation(location.latitude, location.longitude)
                distanceBetweenLocations = locationA.distanceTo(locationB)
            }

            if (location == null || distanceBetweenLocations > MAX_DISTANCE_UNTIL_NEW_LOCATION) {
                // Create new location entry
                Timber.d("Add new Location to the database!")
                location = Location(
                    firstDiscovery = discoveryDate,
                    longitude = longitude,
                    latitude = latitude,
                    altitude = altitude,
                    accuracy = accuracy,
                )
                locationRepository.insert(location)
            }
            else {
                // If location is within the set limit, just use that location and update lastSeen
                Timber.d("Location already in the database... Updating the last seen date!")
                location.lastSeen = discoveryDate
                if (altitude != null) {
                    location.altitude = altitude
                }
                if (accuracy != null && (location.accuracy == null || location.accuracy!! > accuracy)) {
                    location.accuracy = accuracy
                    location.longitude = longitude
                    location.latitude = latitude
                }
                locationRepository.update(location)
            }

            Timber.d("Location: $location")
        }
        return location
    }
}