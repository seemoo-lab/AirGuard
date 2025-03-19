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
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetwork
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungFindMyMobile
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTracker
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.Utility.LocationLogger
import de.seemoo.at_tracking_detection.util.ble.BLEScanCallback
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
        if (SharedPrefs.deactivateBackgroundScanning) {
            Timber.d("Background scanning is deactivated")
            return BackgroundScanResults(0, 0, 0, true)
        }

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
        bluetoothAdapter?.bluetoothLeScanner?.let { scanner ->
            BLEScanCallback.startScanning(
                scanner,
                DeviceManager.scanFilter,
                scanSettings,
                leScanCallback
            )
        } ?: run {
            Timber.e("Bluetooth LE Scanner is null, cannot perform scan.")
            isScanning = false
            return BackgroundScanResults(0, 0, 0, true)
        }

        val scanDuration: Long = getScanDuration()
        delay(scanDuration)
        bluetoothAdapter?.bluetoothLeScanner?.let { scanner ->
            BLEScanCallback.stopScanning(scanner)
        }
        isScanning = false

        Timber.d("Scanning for bluetooth le devices stopped!. Discovered ${scanResultDictionary.size} devices")

        //Waiting for updated location to come in
        Timber.d("Waiting for location update")
        LocationLogger.log("BackgroundBluetoothScanner: Request fetched Location")
        val fetchedLocation = waitForRequestedLocation()
        Timber.d("Fetched location? $fetchedLocation")
        LocationLogger.log("BackgroundBluetoothScanner: Could location be Fetched?: $fetchedLocation")
        if (location == null) {
            LocationLogger.log("BackgroundBluetoothScanner: Failed to fetch location, get last known location and ignore requirements")
            // Get the last location no matter if the requirements match or not
            location = locationProvider.getLastLocation(checkRequirements = false)
            if (location == null) {
                LocationLogger.log("BackgroundBluetoothScanner: Failed to retrieve location again, location is null")
            } else {
                LocationLogger.log("BackgroundBluetoothScanner: Got Location: Latitude: ${location!!.latitude}, Longitude: ${location!!.longitude}, Altitude: ${location!!.altitude}, Accuracy: ${location!!.accuracy}")
            }
        } else {
            LocationLogger.log("BackgroundBluetoothScanner: Fetched Location: Latitude: ${location!!.latitude}, Longitude: ${location!!.longitude}, Altitude: ${location!!.altitude}, Accuracy: ${location!!.accuracy}")
        }

        val validDeviceTypes = DeviceType.getAllowedDeviceTypesFromSettings()

        //Adding all scan results to the database after the scan has finished
        scanResultDictionary.forEach { (_, discoveredDevice) ->
            val deviceType = discoveredDevice.wrappedScanResult.deviceType
            val skipDevice = Utility.getSkipDevice(wrappedScanResult = discoveredDevice.wrappedScanResult)

            if (deviceType in validDeviceTypes && !skipDevice) {
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
            if (BuildConfig.DEBUG) {
                val dbLocation = saveLocation(latitude = location?.latitude, longitude = location?.longitude, accuracy = location?.accuracy, altitude = location?.altitude, discoveryDate = LocalDateTime.now())
                scan.locationId = dbLocation?.locationId
                scan.locationDeg = "${location?.longitude},${location?.latitude}"
            }
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

    fun getScanMode(): Int {
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

                // For 15 minute algorithm
                var overrideIdentifier: String? = null
                if (wrappedScanResult.connectionState !in DeviceManager.unsafeConnectionState && wrappedScanResult.deviceType in DeviceManager.savedDeviceTypesWith15MinuteAlgorithm && wrappedScanResult.connectionState in DeviceManager.savedConnectionStatesWith15MinuteAlgorithm){
                    Timber.d("Device ${wrappedScanResult.uniqueIdentifier} ${wrappedScanResult.deviceType} ${wrappedScanResult.connectionState} is in a saved connection state for the 15 Minute Algorithm!")
                    overrideIdentifier = deviceSaved.address
                }

                val beaconSaved = saveBeacon(wrappedScanResult, discoveryDate, locId, overrideIdentifier) ?: return@withLock Pair(null, null)

                return@withLock Pair(deviceSaved, beaconSaved)
            }
        }
    }

    private suspend fun saveBeacon(
        wrappedScanResult: ScanResultWrapper,
        discoveryDate: LocalDateTime,
        locId: Int?,
        overrideIdentifier: String? = null
    ): Beacon? {
        return withContext(Dispatchers.IO) {
            beaconMutex.withLock {
                val beaconRepository =
                    ATTrackingDetectionApplication.getCurrentApp().beaconRepository
                val uuids = wrappedScanResult.serviceUuids
                val uniqueIdentifier = overrideIdentifier ?: wrappedScanResult.uniqueIdentifier

                val connectionState: ConnectionState = wrappedScanResult.connectionState
                val connectionStateString = Utility.connectionStateToString(connectionState)

                var beacon: Beacon? = null
                val beacons = beaconRepository.getDeviceBeaconsSince(
                    deviceAddress = uniqueIdentifier,
                    since = discoveryDate.minusMinutes(TIME_BETWEEN_BEACONS)
                ) // sorted by newest first

                if (beacons.isEmpty()) {
                    Timber.d("Add new Beacon to the database!")

                    beacon = if (wrappedScanResult.deviceType == DeviceType.SAMSUNG_TRACKER && wrappedScanResult.connectionState in DeviceManager.savedConnectionStatesWith15MinuteAlgorithm) {
                        // Samsung Tracker have Aging Counter, we use this to further optimize the 15 Minute Algorithm
                        Beacon(
                            discoveryDate,
                            wrappedScanResult.rssiValue,
                            uniqueIdentifier,
                            locId,
                            SamsungTracker.getInternalAgingCounter(wrappedScanResult.scanResult),
                            uuids,
                            connectionStateString
                        )
                    } else if (BuildConfig.DEBUG) {
                        // Save the manufacturer data to the beacon
                        Beacon(
                            discoveryDate,
                            wrappedScanResult.rssiValue,
                            uniqueIdentifier,
                            locId,
                            wrappedScanResult.mfg,
                            uuids,
                            connectionStateString
                        )
                    } else {
                        Beacon(
                            discoveryDate,
                            wrappedScanResult.rssiValue,
                            uniqueIdentifier,
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

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun saveDevice(
        wrappedScanResult: ScanResultWrapper,
        discoveryDate: LocalDateTime
    ): BaseDevice? {
        return withContext(Dispatchers.IO) {
            deviceMutex.withLock {
                val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
                val deviceAddress = wrappedScanResult.uniqueIdentifier

                // Checks if Device already exists in device database
                var device = deviceRepository.getDevice(deviceAddress)
                if (device == null) {
                    device = BaseDevice(wrappedScanResult.scanResult)

                    // Check if ConnectionState qualifies Device to be saved
                    // Only Save when Device is in Overmature Offline Mode or qualifies for the 15 Minute Algorithm
                    if (wrappedScanResult.connectionState in DeviceManager.savedConnectionStates || Pair(wrappedScanResult.deviceType, wrappedScanResult.connectionState) in DeviceManager.additionalSavedConnectionStates) {
                        if (wrappedScanResult.connectionState !in DeviceManager.unsafeConnectionState && Pair(wrappedScanResult.deviceType, wrappedScanResult.connectionState) !in DeviceManager.additionalSavedConnectionStates) {
                            // Timber.d("Device is safe and will be hidden to the user!")
                            device.safeTracker = true
                        }

                        if (wrappedScanResult.deviceType == DeviceType.GOOGLE_FIND_MY_NETWORK) {
                            val alternativeIdentifier = GoogleFindMyNetwork.getAlternativeIdentifier(wrappedScanResult.scanResult)
                            if (alternativeIdentifier != null) {
                                deviceRepository.getDeviceWithAlternativeIdentifier(alternativeIdentifier)?.let {
                                    Timber.d("Google Device already in the database with alternative identifier... Updating the last seen date!")
                                    device = it
                                    device.lastSeen = discoveryDate
                                    deviceRepository.update(device)
                                    return@withLock device
                                }
                            }
                        }

                        Timber.d("Add new Device to the database!")
                        deviceRepository.insert(device)
                    } else if (wrappedScanResult.deviceType in DeviceManager.savedDeviceTypesWith15MinuteAlgorithm && wrappedScanResult.connectionState in DeviceManager.savedConnectionStatesWith15MinuteAlgorithm) {
                        Timber.d("Called 15 minute algorithm for device ${wrappedScanResult.uniqueIdentifier} ${wrappedScanResult.deviceType} ${wrappedScanResult.connectionState}")
                        var timeTolerance: Long = 5 // in minutes
                        val deviceType = wrappedScanResult.deviceType
                        val connectionState = wrappedScanResult.connectionState
                        val trackerProperties: Byte? = when (deviceType) {
                            DeviceType.SAMSUNG_TRACKER -> SamsungTracker.getPropertiesByte(wrappedScanResult.scanResult)
                            DeviceType.SAMSUNG_FIND_MY_MOBILE -> SamsungFindMyMobile.getPropertiesByte(wrappedScanResult.scanResult)
                            else -> null
                        }

                        if (trackerProperties == null) {
                            Timber.d("Device does not have Hardware Properties Byte in Advertisement... Skipping!")
                            return@withLock null
                        }

                        val baseInterval = 15

                        val correspondingDevice: BaseDevice? = if (deviceType in DeviceManager.strict15MinuteAlgorithm) {
                            Timber.d("Device is in strict 15 Minute Algorithm! Checking aging Counter")
                            // Additional Check: Aging Counter for Samsung Tracker (e.g. SmartTags) has to be exactly 1 smaller in the previous Beacon
                            val currentAgingCounter: ByteArray? = SamsungTracker.getInternalAgingCounter(wrappedScanResult.scanResult)
                            Timber.d("Current Aging Counter: ${currentAgingCounter?.toHexString(format = HexFormat.UpperCase)}")
                            if (currentAgingCounter == null) {
                                Timber.d("Current Aging Counter is null... Skipping!")
                                return@withLock null
                            }

                            timeTolerance = timeTolerance + 15

                            val since = discoveryDate.minusMinutes(baseInterval + 15 + timeTolerance)
                            val until = discoveryDate.minusMinutes(baseInterval - timeTolerance)

                            val previousAgingCounter = SamsungTracker.decrementAgingCounter(
                                currentAgingCounter,
                                1 // decrementAmount
                            )

                            val additionalDataString = SamsungTracker.calculateAdditionalDataString(
                                connectionState = connectionState,
                                agingCounter = previousAgingCounter,
                                flags = trackerProperties
                            )

                            val deviceBefore: BaseDevice? = deviceRepository.getDeviceWithRecentBeacon(
                                deviceType = deviceType,
                                additionalData = additionalDataString,
                                since = since,
                                until = until,
                            )
                            Timber.d("Device Before: $deviceBefore")

                            if (deviceBefore == null) {
                                Timber.d("Previous Device with the aging Counter could not be found... Skipping!")
                                null
                            } else {
                                Timber.d("Previous Device with the aging Counter found! Aging Counter check passed!")
                                deviceBefore
                            }
                        } else {
                            Timber.d("Device is not in strict 15 Minute Algorithm! Checking for any Device in the last 15 Minutes")
                            val since = discoveryDate.minusMinutes(baseInterval + 15 + timeTolerance)
                            val until = discoveryDate.minusMinutes(baseInterval - timeTolerance)

                            val additionalDataString = SamsungTracker.calculateAdditionalDataString(
                                connectionState = connectionState,
                                agingCounter = byteArrayOf(0x00, 0x00, 0x00),
                                flags = trackerProperties
                            )

                            deviceRepository.getDeviceWithRecentBeacon(
                                deviceType = deviceType,
                                additionalData = additionalDataString,
                                since = since,
                                until = until
                            )
                        }

                        // Calculate new additionalDataString
                        val additionalDataString = SamsungTracker.calculateAdditionalDataString(
                            connectionState = connectionState,
                            agingCounter = if (deviceType in DeviceManager.strict15MinuteAlgorithm) {
                                SamsungTracker.getInternalAgingCounter(wrappedScanResult.scanResult)!!
                            } else {
                                byteArrayOf(0x00, 0x00, 0x00)
                            },
                            flags = trackerProperties
                        )

                        if (correspondingDevice == null) {
                            Timber.d("Add new Device to the database using the 15 Minute Algorithm!")
                            device.additionalData = additionalDataString
                            deviceRepository.insert(device)
                        } else {
                            Timber.d("Update Device to the database using the 15 Minute Algorithm!")
                            device = correspondingDevice
                            device.lastSeen = discoveryDate
                            device.additionalData = additionalDataString
                            deviceRepository.update(device)
                        }
                    } else {
                        Timber.d("Device not in a saved connection state... Skipping!")
                        return@withLock null
                    }
                } else {
                    Timber.d("Device already in the database... Updating the last seen date!")
                    device.lastSeen = discoveryDate
                    deviceRepository.update(device)
                }

                if (device.deviceType == DeviceType.GOOGLE_FIND_MY_NETWORK) {
                    Timber.d("Google Find My Network Device found! Detecting Subtype...")
                    val subtype = GoogleFindMyNetwork.getSubType(wrappedScanResult)
                    device.subDeviceType = GoogleFindMyNetworkType.subTypeToString(subtype)
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