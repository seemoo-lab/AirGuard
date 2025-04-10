package de.seemoo.at_tracking_detection.detection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
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
import de.seemoo.at_tracking_detection.util.privacyPrint
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.Executors
import kotlin.math.abs

@RequiresApi(Build.VERSION_CODES.S)
object PermanentBluetoothScanner: LocationHistoryListener {
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var pendingFoundDevices: ArrayList<BackgroundBluetoothScanner.DiscoveredDevice> =
        ArrayList()

    /**
     * Devices that have been recently seen. So we don't need to add them to the database again
     */
    private var recentlySeenDevices: ArrayList<BackgroundBluetoothScanner.DiscoveredDevice> =
        ArrayList()

    /**
     * The duration how long a device remains in the recently seen. 7 min.
     * Afterward, the device can be added to the DB again with a new sighting.
     */
    private val COOL_DOWN_TIME_MS = 420_000
    private val MAX_LOCATION_AGE_S = 300

    private val applicationContext: Context
        get() {
            return ATTrackingDetectionApplication.getAppContext()
        }

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

    private var isWaitingForLocationUpdate = false


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

    private val validDeviceTypes = DeviceType.getAllowedDeviceTypesFromSettings()

    private val scanRepository: ScanRepository
        get() {
            return ATTrackingDetectionApplication.getCurrentApp().scanRepository
        }

    private var isScanning = false


    private var executor = Executors.newFixedThreadPool(2)
    private var locationExecutor = Executors.newSingleThreadExecutor()

    fun scan() {

        if (!Utility.checkBluetoothPermission()) {
            BLELogger.d("Permission to perform bluetooth scan missing")
            return
        }

        // Scan permanently on a background thread
        if (isScanning) {
            Timber.d("App is already scanning in background. Stopping ongoing scans and restarting.")
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
            executor.shutdownNow()
            executor = Executors.newFixedThreadPool(2)
            Timber.d("Ongoing scans have been stopped.")
        }

        if (SharedPrefs.deactivateBackgroundScanning) {
            BLELogger.d("Background scanning is deactivated")
        }

        BLELogger.i("Launching new thread for background scanning")

        // Launch a new thread
        executor.execute(kotlinx.coroutines.Runnable {
            BLELogger.i("Thread for background scanning started")

            BLELogger.d("Starting permanent Bluetooth scanner")
            // For a permanent scanner, we use low power
            val scanMode = ScanSettings.SCAN_MODE_LOW_POWER

            try {
                val bluetoothManager =
                    applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                bluetoothAdapter = bluetoothManager.adapter
                if (bluetoothAdapter == null) {
                    BLELogger.e("BluetoothAdapter is null, cannot perform scan.")
                } else if (!bluetoothAdapter!!.isEnabled) {
                    BLELogger.e("Bluetooth is disabled, cannot perform scan.")
                } else if (bluetoothAdapter!!.bluetoothLeScanner == null) {
                    BLELogger.e("BLE is not supported on this device.")
                }
            } catch (e: Throwable) {
                BLELogger.e("BluetoothAdapter not found or BLE not supported!")
            }

            isScanning = true
            location = null


            // Starting BLE Scan
            BLELogger.d("Start Scanning for bluetooth le devices...")
            val scanSettings = ScanSettings.Builder()
                .setScanMode(scanMode)
                // Adding other scan settings results in not receiving any devices in the callback
//                .setReportDelay(5_000)
//                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                .build()



            bluetoothAdapter?.bluetoothLeScanner?.startScan(
                DeviceManager.scanFilter,
                scanSettings,
                leScanCallback
            )
                ?: run {
                    BLELogger.e("Bluetooth LE Scanner is null, cannot perform scan.")
                    isScanning = false
                }

            LocationHistoryController.listenToLocationChanges(this)
            BLELogger.d("Requesting fused location updates")
            locationProvider.requestFusedBackgroundLocationUpdates(
                locationExecutor,
                LocationHistoryController
            )
            BLELogger.d("Requesting passive location updates")
            locationProvider.requestPassiveLocationProviderUpdates(LocationHistoryController)

//            while (true) {
//                BLELogger.i( "Keeping permanent scanner active.")
//                Thread.sleep(10000);
//            }
        })
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
                recentlySeenDevices.firstOrNull { it.wrappedScanResult.deviceAddress == device.wrappedScanResult.deviceAddress }?.discoveryDate
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
        }

        if (!isWaitingForLocationUpdate || (Date().time - (LocationHistoryController.lastLocationUpdate?.time
                ?: 0)) < MAX_LOCATION_AGE_S * 1000
        ) {
            insertPendingDevices()
        }
    }

    private suspend fun insertPendingDevices() {
        if (pendingFoundDevices.size == 0) {
            return
        }

        deviceMutex.withLock {

            // We go through the devices and find the closest location in time for each device
            BLELogger.d("Starting to match devices and locations")

            val savedDevices = ArrayList<BackgroundBluetoothScanner.DiscoveredDevice>()

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

//                BLELogger.d("${device.wrappedScanResult.uniqueIdentifier}: Found a location with ${timeDiff}s difference ${location?.privacyPrint()})")
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

                if (pair.first != null && pair.second != null) {
                    BLELogger.d("Inserted device ${pair.first?.address} (${pair.first?.deviceType}) at ${pair.second?.locationId} to the DB")
                    // Logging this as a scan
                    scanRepository.insert(
                        Scan(
                            endDate = device.discoveryDate,
                            duration = 0,
                            noDevicesFound = 1,
                            isManual = false,
                            scanMode = ScanSettings.SCAN_MODE_LOW_POWER,
                            startDate = device.discoveryDate,
                            locationDeg = "${PermanentBluetoothScanner.location?.longitude},${PermanentBluetoothScanner.location?.latitude}",
                            locationId = pair.second?.locationId,
                            devicesAddressesFound = pair.first?.address,
                            devicesTypesFound = pair.first?.deviceType?.name
                        )
                    )

                    SharedPrefs.lastScanDate = device.discoveryDate
                }else {
                    BLELogger.d("Device ${device.wrappedScanResult.deviceAddress} not added to DB")
                }
            }

            if (savedDevices.size == 0 && pendingFoundDevices.size > 0) {
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
            val wrappedScanResult = ScanResultWrapper(scanResult)
            //Checks if the device has been found already

            val device =
                BackgroundBluetoothScanner.DiscoveredDevice(wrappedScanResult, LocalDateTime.now())
            CoroutineScope(Dispatchers.IO).launch {
                foundTracker(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            BLELogger.e("Bluetooth scan failed $errorCode")
            if (BuildConfig.DEBUG) {
                notificationService.sendBLEErrorNotification()
            }
            CoroutineScope(Dispatchers.IO).launch {
                Thread.sleep(2_000)
                PermanentBluetoothScanner.scan()
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