package de.seemoo.at_tracking_detection.detection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Location
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.ble.BLEScanCallback
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object BackgroundBluetoothScanner {
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var scanResultDictionary: ConcurrentHashMap<String, DiscoveredDevice> =
        ConcurrentHashMap()

    private var applicationContext: Context = ATTrackingDetectionApplication.getAppContext()

    // Mutexes for scheduling when adding to the database to avoid double entries
    private val insertScanResultMutex = Mutex()
    private val beaconMutex = Mutex()
    private val deviceMutex = Mutex()
    private val locationMutex = Mutex()

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

    private val scanRepository: ScanRepository
        get() {
            return ATTrackingDetectionApplication.getCurrentApp().scanRepository
        }

    private var isScanning = false

    class BackgroundScanResults(
        var duration: Long,
        var scanMode: Int,
        var numberDevicesFound: Int,
        var failed: Boolean
    )

    suspend fun scanInBackground(startedFrom: String): BackgroundScanResults {
        if (isScanning) {
            Timber.w("BackgroundBluetoothScanner scan already running")
            return BackgroundScanResults(0, 0, 0, true)
        }

        Timber.d("Starting BackgroundBluetoothScanner from $startedFrom")
        val scanMode = getScanMode()
        val scanId = scanRepository.insert(
            Scan(
                startDate = LocalDateTime.now(),
                isManual = false,
                scanMode = scanMode
            )
        )

        if (!Utility.checkBluetoothPermission()) {
            Timber.d("Permission to perform bluetooth scan missing")
            return BackgroundScanResults(0, 0, 0, true)
        }
        try {
            val bluetoothManager =
                applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter == null) {
                Timber.e("BluetoothAdapter is null, cannot perform scan.")
                return BackgroundScanResults(0, 0, 0, true)
            } else if (!bluetoothAdapter!!.isEnabled) {
                Timber.e("Bluetooth is disabled, cannot perform scan.")
                return BackgroundScanResults(0, 0, 0, true)
            } else if (bluetoothAdapter!!.bluetoothLeScanner == null) {
                Timber.e("BLE is not supported on this device.")
                return BackgroundScanResults(0, 0, 0, true)
            }
        } catch (e: Throwable) {
            Timber.e("BluetoothAdapter not found or BLE not supported!")
            return BackgroundScanResults(0, 0, 0, true)
        }

        scanResultDictionary = ConcurrentHashMap()
        isScanning = true
        location = null

        // Set a wake lock to keep the CPU running while we complete the scanning
        val powerManager =
            applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock: PowerManager.WakeLock? = powerManager.run {
            try {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
                    acquire(5 * 60 * 1000L /*5 minutes*/)
                }
            } catch (e: SecurityException) {
                Timber.w("Failed to acquire wake lock: ${e.message}")
                null
            }
        }

        val useLocation = SharedPrefs.useLocationInTrackingDetection
        if (useLocation) {
            // Returns the last known location if this matches our requirements or starts new location updates
            locationFetchStarted = System.currentTimeMillis()
            location = locationProvider.lastKnownOrRequestLocationUpdates(
                locationRequester = locationRequester,
                timeoutMillis = LOCATION_UPDATE_MAX_TIME_MS - 2000L
            )
            if (location == null) {
                Timber.e("Failed to retrieve location")
            }
        }

        // Starting BLE Scan
        Timber.d("Start Scanning for bluetooth le devices...")
        val scanSettings = ScanSettings.Builder().setScanMode(scanMode).build()

        SharedPrefs.isScanningInBackground = true
        BLEScanCallback.startScanning(
            bluetoothAdapter!!.bluetoothLeScanner,
            DeviceManager.scanFilter,
            scanSettings,
            leScanCallback
        )

        val scanDuration: Long = getScanDuration()
        delay(scanDuration)
        BLEScanCallback.stopScanning(bluetoothAdapter!!.bluetoothLeScanner)
        isScanning = false

        Timber.d("Scanning for bluetooth le devices stopped!. Discovered ${scanResultDictionary.size} devices")

        //Waiting for updated location to come in
        Timber.d("Waiting for location update")
        val fetchedLocation = waitForRequestedLocation()
        Timber.d("Fetched location? $fetchedLocation")
        if (location == null) {
            // Get the last location no matter if the requirements match or not
            location = locationProvider.getLastLocation(checkRequirements = false)
        }

        val validDeviceTypes = DeviceType.getAllowedDeviceTypesFromSettings()

        //Adding all scan results to the database after the scan has finished
        scanResultDictionary.forEach { (_, discoveredDevice) ->
            val deviceType = discoveredDevice.wrappedScanResult.deviceType

            if (deviceType in validDeviceTypes) {
                insertScanResult(
                    wrappedScanResult = discoveredDevice.wrappedScanResult,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    altitude = location?.altitude,
                    accuracy = location?.accuracy,
                    discoveryDate = discoveredDevice.discoveryDate,
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

        // Release the wake lock when we are done
        wakeLock?.release()

        Timber.d("Finished Background Scan")
        return BackgroundScanResults(
            duration = scanDuration,
            scanMode = scanMode,
            numberDevicesFound = scanResultDictionary.size,
            failed = false
        )
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scanResult: ScanResult) {
            super.onScanResult(callbackType, scanResult)
            val wrappedScanResult = ScanResultWrapper(scanResult)
            //Checks if the device has been found already
            if (!scanResultDictionary.containsKey(wrappedScanResult.uniqueIdentifier)) {
                Timber.d("Found ${wrappedScanResult.uniqueIdentifier} at ${LocalDateTime.now()}")
                scanResultDictionary[wrappedScanResult.uniqueIdentifier] =
                    DiscoveredDevice(wrappedScanResult, LocalDateTime.now())
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
            Timber.d("Got location in ${(System.currentTimeMillis() - started) / 1000}s")
            this@BackgroundBluetoothScanner.location = location
            this@BackgroundBluetoothScanner.locationRetrievedCallback?.let { it() }
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

    class DiscoveredDevice(
        var wrappedScanResult: ScanResultWrapper,
        var discoveryDate: LocalDateTime
    )

    const val MAX_DISTANCE_UNTIL_NEW_LOCATION: Float = 150f // in meters
    const val TIME_BETWEEN_BEACONS: Long =
        15 // 15 minutes until the same beacon gets saved again in the db
    private const val LOCATION_UPDATE_MAX_TIME_MS: Long =
        122_000L // Wait maximum 122s to get a location update

    suspend fun insertScanResult(
        wrappedScanResult: ScanResultWrapper,
        latitude: Double?,
        longitude: Double?,
        altitude: Double?,
        accuracy: Float?,
        discoveryDate: LocalDateTime,
    ): Pair<BaseDevice?, Beacon?> {
        return withContext(Dispatchers.IO) {
            insertScanResultMutex.withLock {
                if (altitude != null && altitude > TrackingDetectorConstants.IGNORE_DEVICE_ABOVE_ALTITUDE) {
                    Timber.d("Ignoring device for locations above ${TrackingDetectorConstants.IGNORE_DEVICE_ABOVE_ALTITUDE}m, we assume the User is on a plane!")
                    // Do not save device at all in case we assume it is on a plane
                    return@withLock Pair(null, null)
                }

                val deviceSaved = saveDevice(wrappedScanResult, discoveryDate) ?: return@withLock Pair(
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
                    saveBeacon(wrappedScanResult, discoveryDate, locId) ?: return@withLock Pair(null, null)

                return@withLock Pair(deviceSaved, beaconSaved)
            }
        }
    }

    private suspend fun saveBeacon(
        wrappedScanResult: ScanResultWrapper,
        discoveryDate: LocalDateTime,
        locId: Int?
    ): Beacon? {
        return withContext(Dispatchers.IO) {
            beaconMutex.withLock {
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
                            discoveryDate,
                            wrappedScanResult.rssiValue,
                            wrappedScanResult.uniqueIdentifier,
                            locId,
                            wrappedScanResult.mfg,
                            uuids,
                            connectionStateString
                        )
                    } else {
                        Beacon(
                            discoveryDate,
                            wrappedScanResult.rssiValue,
                            wrappedScanResult.uniqueIdentifier,
                            locId,
                            null,
                            uuids,
                            connectionStateString
                        )
                    }
                    beaconRepository.insert(beacon)
                } else if (beacons[0].locationId == null && locId != null && locId != 0) {
                    Timber.d("Beacon already in the database... Adding Location")
                    beacon = beacons[0]
                    beacon.locationId = locId
                    if (beacon.connectionState == "UNKNOWN" && connectionState != ConnectionState.UNKNOWN) {
                        beacon.connectionState = connectionStateString
                    }
                    beaconRepository.update(beacon)
                }

                Timber.d("Beacon: $beacon")
                return@withLock beacon
            }
        }
    }

    private suspend fun saveDevice(
        wrappedScanResult: ScanResultWrapper,
        discoveryDate: LocalDateTime
    ): BaseDevice? {
        return withContext(Dispatchers.IO) {
            deviceMutex.withLock {
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
                        return@withLock null
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
                return@withLock device
            }
        }
    }

    private suspend fun saveLocation(
        latitude: Double?,
        longitude: Double?,
        altitude: Double?,
        discoveryDate: LocalDateTime,
        accuracy: Float?
    ): Location? {
        return withContext(Dispatchers.IO) {
            locationMutex.withLock {
                if (altitude != null && altitude > TrackingDetectorConstants.IGNORE_LOCATION_ABOVE_ALTITUDE) {
                    Timber.d("Ignoring location above ${TrackingDetectorConstants.IGNORE_LOCATION_ABOVE_ALTITUDE}m, we assume the User might be on a plane!")
                    // Do not save location object
                    return@withLock null
                }

                val locationRepository = ATTrackingDetectionApplication.getCurrentApp().locationRepository

                // set location to null if gps location could not be retrieved
                var location: Location? = null

                if (latitude != null && longitude != null) {
                    // Get closest location from database
                    location = locationRepository.closestLocation(latitude, longitude)

                    var distanceBetweenLocations: Float = Float.MAX_VALUE

                    if (location != null) {
                        val locationA = TrackingDetectorWorker.getLocation(latitude, longitude)
                        val locationB = TrackingDetectorWorker.getLocation(location.latitude, location.longitude)
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
                    } else {
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
                return@withLock location
            }
        }
    }
}