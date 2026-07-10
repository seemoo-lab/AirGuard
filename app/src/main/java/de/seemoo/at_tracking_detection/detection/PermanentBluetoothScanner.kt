package de.seemoo.at_tracking_detection.detection

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.Utility.BLELogger
import de.seemoo.at_tracking_detection.util.ble.BluetoothStateMonitor
import de.seemoo.at_tracking_detection.util.ble.DeviceSubTypeDetector
import de.seemoo.at_tracking_detection.util.ble.ScanOrchestrator
import de.seemoo.at_tracking_detection.util.privacyPrint
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

@RequiresApi(Build.VERSION_CODES.S)
object PermanentBluetoothScanner: LocationHistoryListener {
    private var bluetoothAdapter: BluetoothAdapter? = null

    /**
     * Daemon thread factory so the executor thread won't block JVM shutdown
     */
    private val daemonThreadFactory = ThreadFactory { r ->
        Thread(r, "PermanentBleScanner").apply { isDaemon = true }
    }

    private var executor = Executors.newSingleThreadExecutor(daemonThreadFactory)
    @Volatile private var keepRunning = false

    private var pendingFoundDevices: ArrayList<BackgroundBluetoothScanner.DiscoveredDevice> =
        ArrayList()

    /**
     * Devices that have been recently seen. So we don't need to add them to the database again
     */
    private var recentlySeenDevices: ArrayList<BackgroundBluetoothScanner.DiscoveredDevice> =
        ArrayList()

    /**
     * The duration how long a device remains in the recently seen. 15 min.
     * Afterward, the device can be added to the DB again with a new sighting.
     */
    private const val COOL_DOWN_TIME_MS = 900_000 // 15 Minutes
    private const val MAX_LOCATION_AGE_S = 300

    /**
     * Duration of one "permanent scan" window for grouping beacons into a single Scan object.
     */
    private const val SCAN_WINDOW_MS = 900_000L // 15 minutes

    /** The current scan window's Scan ID (from DB). Null if no window is active. */
    @Volatile private var currentScanId: Long? = null
    /** Start time of the current scan window. */
    @Volatile private var currentScanWindowStart: LocalDateTime? = null
    /** Addresses found in the current scan window. */
    private val currentWindowAddresses = mutableListOf<String>()
    /** Device types found in the current scan window. */
    private val currentWindowTypes = mutableListOf<String>()

    private val applicationContext: Context
        get() {
            return ATTrackingDetectionApplication.getAppContext()
        }

    // Mutexes for scheduling when adding to the database to avoid double entries
    private val insertScanResultMutex = Mutex()
    private val beaconMutex = Mutex()
    private val deviceMutex = Mutex()
    private val locationMutex = Mutex()

    private val currentlyProcessingGatt = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    var location: android.location.Location? = null
        set(value) {
            field = value
            if (value != null) {
                locationRetrievedCallback?.let { it() }
            }
        }

    private var locationRetrievedCallback: (() -> Unit)? = null

    private var locationFetchStarted: Long? = null

    private var isWaitingForLocationUpdate = false


    val backgroundWorkScheduler: BackgroundWorkScheduler
        get() {
            return ATTrackingDetectionApplication.getCurrentApp()?.backgroundWorkScheduler
                ?: error("ATTrackingDetectionApplication not initialized")
        }

    val notificationService: NotificationService
        get() {
            return ATTrackingDetectionApplication.getCurrentApp()?.notificationService
                ?: error("ATTrackingDetectionApplication not initialized")
        }
    private val locationProvider: LocationProvider
        get() {
            return ATTrackingDetectionApplication.getCurrentApp()?.locationProvider
                ?: error("ATTrackingDetectionApplication not initialized")
        }

    private val validDeviceTypes = DeviceType.getAllowedDeviceTypesFromSettings()

    private val scanRepository: ScanRepository
        get() {
            return ATTrackingDetectionApplication.getCurrentApp()?.scanRepository
                ?: error("ATTrackingDetectionApplication not initialized")
        }

    private var isScanning = false

    // ── PendingIntent-based scanning for Android 15+ ──────────────────────

    private const val ACTION_PERMANENT_SCAN = "de.seemoo.at_tracking_detection.PERMANENT_BLE_SCAN"
    private const val REQUEST_CODE_PERMANENT_SCAN = -200

    @Volatile private var pendingIntentScanActive = false

    /** Guard against re-entrant calls to startPendingIntentScan */
    @Volatile private var pendingIntentScanInProgress = false

    /**
     * Listener that re-registers the PendingIntent scan after Bluetooth is
     * toggled off→on.
     * PendingIntent scans are cleared by the system when Bluetooth turns off.
     */
    private val bluetoothStateListener = object : BluetoothStateMonitor.Listener {
        override fun onBluetoothStateChanged(enabled: Boolean) {
            // Skip if already inside startPendingIntentScan()
            if (pendingIntentScanInProgress) return

            if (enabled
                && pendingIntentScanActive
                && SharedPrefs.usePermanentBluetoothScanner
                && !SharedPrefs.deactivateBackgroundScanning
            ) {
                BLELogger.d("Bluetooth re-enabled, re-registering PendingIntent scan")
                startPendingIntentScan()
            }
        }
    }
    private var bluetoothListenerRegistered = false

    // ── Public API ────────────────────────────────────────────────────────

    fun scan() {
        if (SharedPrefs.deactivateBackgroundScanning) {
            BLELogger.d("Background scanning is deactivated")
            return
        } else if (!Utility.checkBluetoothPermission()) {
            BLELogger.d("Permission to perform bluetooth scan missing")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Android 15+: PendingIntent-based scanning
            startPendingIntentScan()
        } else {
            // Android 12-14: callback-based scanning via ScanOrchestrator
            startCallbackScan()
        }
    }

    fun stopPermanentScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            stopPendingIntentScan()
        } else {
            stopCallbackScan()
        }
    }

    // ── PendingIntent scan (Android 15+) ──────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startPendingIntentScan() {
        if (SharedPrefs.deactivateBackgroundScanning) return
        if (!Utility.checkBluetoothPermission()) return

        // Prevent re-entrant calls
        if (pendingIntentScanInProgress) return
        pendingIntentScanInProgress = true

        try {
            val manager = applicationContext
                .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter ?: run {
                BLELogger.d("PendingIntent scan: adapter null")
                return
            }
            if (!adapter.isEnabled) {
                BLELogger.d("PendingIntent scan: Bluetooth disabled")
                return
            }
            val scanner = try { adapter.bluetoothLeScanner } catch (_: Throwable) { null }
            if (scanner == null) {
                BLELogger.d("PendingIntent scan: scanner null")
                return
            }

            val intent = Intent(applicationContext, PermanentScanReceiver::class.java).apply {
                action = ACTION_PERMANENT_SCAN
            }
            val pi = PendingIntent.getBroadcast(
                applicationContext,
                REQUEST_CODE_PERMANENT_SCAN,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()

            val errorCode = scanner.startScan(DeviceManager.scanFilter, scanSettings, pi)
            if (errorCode == 0) {
                pendingIntentScanActive = true
                BLELogger.i("PendingIntent BLE scan started successfully")
            } else {
                BLELogger.e("PendingIntent BLE scan failed with error: $errorCode")
            }

            // Register the Bluetooth state listener so we re-register after BT toggles.
            // Set the flag BEFORE addListener because addListener fires the callback
            // synchronously; the re-entrancy guard above will block that callback.
            if (!bluetoothListenerRegistered) {
                bluetoothListenerRegistered = true
                BluetoothStateMonitor.addListener(bluetoothStateListener)
            }
        } catch (t: Throwable) {
            BLELogger.e("PendingIntent scan start failed: ${t.message}")
        } finally {
            pendingIntentScanInProgress = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopPendingIntentScan() {
        pendingIntentScanActive = false
        try {
            val manager = applicationContext
                .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val scanner = manager.adapter?.bluetoothLeScanner
            if (scanner != null && Utility.checkBluetoothPermission()) {
                val intent = Intent(applicationContext, PermanentScanReceiver::class.java).apply {
                    action = ACTION_PERMANENT_SCAN
                }
                val pi = PendingIntent.getBroadcast(
                    applicationContext,
                    REQUEST_CODE_PERMANENT_SCAN,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE
                )
                if (pi != null) {
                    scanner.stopScan(pi)
                    pi.cancel()
                }
            }
        } catch (_: Throwable) {
            // Best-effort stop
        }

        if (bluetoothListenerRegistered) {
            BluetoothStateMonitor.removeListener(bluetoothStateListener)
            bluetoothListenerRegistered = false
        }
    }

    // ── Callback-based scan (Android 12-14) ───────────────────────────────

    private fun startCallbackScan() {
        keepRunning = true

        // Avoid creating multiple loops
        executor.execute {
            BLELogger.i("Permanent scanning loop started")
            val scanMode = ScanSettings.SCAN_MODE_LOW_POWER
            val scanSettings = ScanSettings.Builder().setScanMode(scanMode).build()
            val filters = DeviceManager.scanFilter

            while (keepRunning) {
                try {
                    // Check adapter/scanner availability
                    val manager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = manager.adapter
                    val scanner = try { adapter?.bluetoothLeScanner } catch (_: Throwable) { null }
                    if (adapter == null || scanner == null || !adapter.isEnabled) {
                        BLELogger.d("Adapter/scanner not ready; sleeping")
                        Thread.sleep(1000)
                        continue
                    }

                    // Ask orchestrator to ensure a running low-power session (our lease)
                    ScanOrchestrator.ensureRunningLease(
                        callerTag = "PermanentBluetoothScanner",
                        filters = filters,
                        settings = scanSettings,
                        callback = leScanCallback
                    )

                    // Sleep a bit before re-asserting; not too often to avoid churn
                    Thread.sleep(3000)

                } catch (ie: InterruptedException) {
                    // If the app wants to stop, executor shutdown will interrupt; exit cleanly
                    BLELogger.d("Permanent scanning loop interrupted")
                    break
                } catch (t: Throwable) {
                    BLELogger.e("Permanent scanning loop error: ${t.message}")
                    // Back off a little to avoid tight loops
                    try { Thread.sleep(1500) } catch (_: InterruptedException) { break }
                }
            }

            // Release the lease when leaving
            ScanOrchestrator.releaseLease("PermanentBluetoothScanner")
            BLELogger.i("Permanent scanning loop finished")
        }
    }

    private fun stopCallbackScan() {
        keepRunning = false
        // Do NOT call shutdownNow; it interrupts threads mid-call and can trigger InterruptedException in the framework.
        executor.shutdown()
        // Optionally, replace the executor with a new one if you need to start again later
        if (executor.isShutdown || executor.isTerminated) {
            executor = Executors.newSingleThreadExecutor(daemonThreadFactory)
        }
        // Ask orchestrator to stop only if we are the current callback
        try {
            ScanOrchestrator.stopScan("PermanentBluetoothScanner", leScanCallback)
        } catch (_: Throwable) {
            // ignore
        }
    }

    /**
     * Called a when a tracker has been found. The tracker will be added to the DB with the current
     * location.
     */
    suspend fun foundTracker(device: BackgroundBluetoothScanner.DiscoveredDevice) {
        if (!device.wrappedScanResult.deviceIsTracking()) return

        deviceMutex.withLock {
            // Check when the device was last seen
            val lastSeen =
                recentlySeenDevices.firstOrNull { it.wrappedScanResult.uniqueIdentifier == device.wrappedScanResult.uniqueIdentifier }?.discoveryDate
            if (lastSeen != null && lastSeen.until(
                    LocalDateTime.now(),
                    ChronoUnit.MILLIS
                ) < COOL_DOWN_TIME_MS
            ) {
                // Device already seen. Ignore
                return
            }

            BLELogger.d("Permanent scanner found ${device.wrappedScanResult.uniqueIdentifier} at ${LocalDateTime.now()}")

            // Remove all duplicates
            pendingFoundDevices =
                ArrayList(pendingFoundDevices.filter { it.wrappedScanResult.uniqueIdentifier != device.wrappedScanResult.uniqueIdentifier })
            pendingFoundDevices.add(device)

            // Mark as recently seen immediately to prevent race conditions with concurrent calls
            recentlySeenDevices =
                ArrayList(recentlySeenDevices.filter { it.wrappedScanResult.uniqueIdentifier != device.wrappedScanResult.uniqueIdentifier })
            recentlySeenDevices.add(device)

            BLELogger.d("${pendingFoundDevices.size} pending devices")
        }

        if (!isWaitingForLocationUpdate || (Date().time - (LocationHistoryController.lastLocationUpdate?.time
                ?: 0)) < MAX_LOCATION_AGE_S * 1000
        ) {
            insertPendingDevices()
        }
    }

    private suspend fun insertPendingDevices() {
        if (pendingFoundDevices.isEmpty()) {
            return
        }

        deviceMutex.withLock {

            // We go through the devices and find the closest location in time for each device
            BLELogger.d("Starting to match devices and locations")

            val savedDevices = ArrayList<BackgroundBluetoothScanner.DiscoveredDevice>()
            val devicesToProcessGatt = mutableListOf<BackgroundBluetoothScanner.DiscoveredDevice>()

            pendingFoundDevices.forEach { device ->
                // Find the closest location
                val deviceTimestamp =
                    device.discoveryDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
//                    device.discoveryDate.toInstant(ZoneOffset.UTC)
//                        .toEpochMilli()
                var location = LocationHistoryController.history.minByOrNull { abs(it.time - deviceTimestamp) }
                if (location == null) {
                    location = locationProvider.getLastLocation(false)
                }
                if (location == null) {
                    BLELogger.d("No location found")
                    return@forEach
                }
                val timeDiff = (location.time - deviceTimestamp) / 1000


                if (abs(timeDiff) > 300) {
                    BLELogger.d("Time difference too large. Location: ${Date(location.time)}, Tracker found: ${device.discoveryDate}")
                    if (location.time < deviceTimestamp) {
                        BLELogger.d("Waiting for the next location update. The current one is too old")
                        return@forEach
                    }
                    BLELogger.d("Inserting without a location.")
                    location = null
                }

                // BLELogger.d("${device.wrappedScanResult.uniqueIdentifier}: Found a location with ${timeDiff}s difference ${location?.privacyPrint()})")
                if (!Utility.getSkipDevice(device.wrappedScanResult.deviceType) && device.wrappedScanResult.deviceType in validDeviceTypes) {
                    BLELogger.d("Inserting ${device.wrappedScanResult.uniqueIdentifier} ${device.wrappedScanResult.deviceType} at ${location?.privacyPrint()}")
                    val pair = BackgroundBluetoothScanner.insertScanResult(
                        device.wrappedScanResult,
                        latitude = location?.latitude,
                        longitude = location?.longitude,
                        altitude = location?.altitude,
                        accuracy = location?.accuracy,
                        discoveryDate = device.discoveryDate
                    )
                    savedDevices.add(device)
                    recentlySeenDevices.add(device)

                    val savedDevice = pair.first
                    val savedBeacon = pair.second
                    if (savedDevice != null && savedBeacon != null) {
                        BLELogger.d("Inserted device ${savedDevice.address} (${savedDevice.deviceType}) at ${savedBeacon.locationId} to the DB")

                        // Identify if a GATT connection should happen
                        // A GATT connection ONLY happens in the background if afterwards the tracker would immediately trigger a notification
                        if (SharedPrefs.autoDetectDeviceTypes) {
                            val beaconRepository = ATTrackingDetectionApplication.getCurrentApp()?.beaconRepository
                            if (beaconRepository != null) {
                                if (TrackingDetectorWorker.shouldThrowNotification(savedDevice, beaconRepository)) {
                                    if (DeviceSubTypeDetector.needsDetection(device.wrappedScanResult)) {
                                        devicesToProcessGatt.add(device)
                                    }
                                }
                            }
                        }

                        // Only count this device in the scan window if its beacon is actually new
                        // (discoveryDate matches what we passed in). saveBeacon may return an
                        // existing beacon if the regular BackgroundScanner already saved it.
                        val beaconIsNew = savedBeacon.receivedAt == device.discoveryDate
                        if (!beaconIsNew) {
                            BLELogger.d("Beacon already existed (likely from regular scan), skipping scan window update")
                        } else {
                            val deviceTypeName = savedDevice.deviceType?.name ?: "UNKNOWN"
                            // Add to the current 15-minute scan window
                            val now = LocalDateTime.now()
                            val windowStart = currentScanWindowStart
                            val windowExpired = windowStart == null ||
                                    windowStart.until(now, ChronoUnit.MILLIS) >= SCAN_WINDOW_MS

                            if (windowExpired) {
                                // Start a new scan window
                                currentWindowAddresses.clear()
                                currentWindowTypes.clear()
                                currentScanWindowStart = now
                                currentWindowAddresses.add(savedDevice.address)
                                currentWindowTypes.add(deviceTypeName)
                                val scanId = scanRepository.insert(
                                    Scan(
                                        endDate = device.discoveryDate,
                                        duration = 0,
                                        noDevicesFound = 1,
                                        isManual = false,
                                        scanMode = ScanSettings.SCAN_MODE_LOW_POWER,
                                        startDate = now,
                                        locationDeg = "${PermanentBluetoothScanner.location?.longitude},${PermanentBluetoothScanner.location?.latitude}",
                                        locationId = savedBeacon.locationId,
                                        devicesAddressesFound = savedDevice.address,
                                        devicesTypesFound = deviceTypeName
                                    )
                                )
                                currentScanId = scanId
                            } else {
                                // Update existing scan window
                                currentWindowAddresses.add(savedDevice.address)
                                currentWindowTypes.add(deviceTypeName)
                                val scanId = currentScanId
                                if (scanId != null) {
                                    val existingScan = scanRepository.scanWithId(scanId.toInt())
                                    if (existingScan != null) {
                                        // windowStart is guaranteed non-null in this branch
                                        existingScan.endDate = device.discoveryDate
                                        existingScan.noDevicesFound = currentWindowAddresses.size
                                        existingScan.duration = (windowStart.until(now, ChronoUnit.SECONDS)).toInt()
                                        existingScan.devicesAddressesFound = currentWindowAddresses.joinToString(",")
                                        existingScan.devicesTypesFound = currentWindowTypes.joinToString(",")
                                        scanRepository.update(existingScan)
                                    }
                                }
                            }

                            SharedPrefs.lastScanDate = device.discoveryDate
                        }
                    }else {
                        BLELogger.d("Device ${device.wrappedScanResult.deviceAddress} not added to DB (already exists or skipped)")
                    }
                } else {
                    BLELogger.d("Skipping device ${device.wrappedScanResult.uniqueIdentifier} because it should not be saved in the current configuration")
                }
            }

            if (savedDevices.isEmpty() && pendingFoundDevices.isNotEmpty()) {
                // No new devices were added.
                isWaitingForLocationUpdate = true
            } else {
                // Remove old devices
                val savedMacAddresses = savedDevices.map { it.wrappedScanResult.deviceAddress }
                pendingFoundDevices =
                    ArrayList(pendingFoundDevices.filter {
                        it.wrappedScanResult.deviceAddress !in savedMacAddresses
                    })
            }

            devicesToProcessGatt.forEach { device ->
                val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository
                if (deviceRepository != null && currentlyProcessingGatt.add(device.wrappedScanResult.uniqueIdentifier)) {
                    BLELogger.d("PermanentBluetoothScanner: Device ${device.wrappedScanResult.uniqueIdentifier} would trigger notification. Attempting GATT detection...")
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            withTimeoutOrNull(15_000L.milliseconds) {
                                DeviceSubTypeDetector.processDetection(device.wrappedScanResult, deviceRepository)
                            }
                        } catch (e: Exception) {
                            BLELogger.e("PermanentBluetoothScanner: GATT detection failed for ${device.wrappedScanResult.uniqueIdentifier}: ${e.message}")
                        } finally {
                            currentlyProcessingGatt.remove(device.wrappedScanResult.uniqueIdentifier)
                        }
                    }
                }
            }


            // Remove old recent devices
            recentlySeenDevices = ArrayList(recentlySeenDevices.filter {
                it.discoveryDate.until(LocalDateTime.now(), ChronoUnit.MILLIS) < COOL_DOWN_TIME_MS
            }
            )

            //Clean up old locations
            LocationHistoryController.cleanUpHistory()
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scanResult: ScanResult) {
            super.onScanResult(callbackType, scanResult)

            SharedPrefs.showSamsungAndroid15BugNotification = false
            SharedPrefs.showGenericBluetoothBugNotification = false

            val wrappedScanResult = ScanResultWrapper(scanResult)
            //Checks if the device has been found already
            val device = BackgroundBluetoothScanner.DiscoveredDevice(wrappedScanResult, LocalDateTime.now())
            CoroutineScope(Dispatchers.IO).launch {
                foundTracker(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)

            if (errorCode == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SharedPrefs.showSamsungAndroid15BugNotification = true
            } else  {
                SharedPrefs.showGenericBluetoothBugNotification = true
            }

            BLELogger.e("Bluetooth scan failed $errorCode")

            if (BuildConfig.DEBUG && SharedPrefs.sendBLEErrorMessages) {
                notificationService.sendBLEErrorNotification()
            }
            CoroutineScope(Dispatchers.IO).launch {
                delay(2_000.milliseconds)
                scan()
            }
        }
    }

    override fun receivedNewLocation(location: android.location.Location) {
        isWaitingForLocationUpdate = false
        BLELogger.d("Permanent scanner got a location update ${location.privacyPrint()} from ${location.provider}")
        CoroutineScope(Dispatchers.IO).launch {
            insertPendingDevices()
        }
    }

    override fun locationHistoryChanged(
        historyController: LocationHistoryController,
        history: ArrayList<android.location.Location>
    ) {

    }

}