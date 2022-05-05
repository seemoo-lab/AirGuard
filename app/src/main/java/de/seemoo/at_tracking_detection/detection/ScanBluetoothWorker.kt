package de.seemoo.at_tracking_detection.detection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.Location
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.Util
import de.seemoo.at_tracking_detection.util.ble.BLEScanCallback
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.util.jar.Manifest

@HiltWorker
class ScanBluetoothWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val beaconRepository: BeaconRepository,
    private val deviceRepository: DeviceRepository,
    private val scanRepository: ScanRepository,
    private val locationProvider: LocationProvider,
    private val notificationService: NotificationService,
    var backgroundWorkScheduler: BackgroundWorkScheduler
) :
    CoroutineWorker(appContext, workerParams) {

    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var scanResultDictionary: HashMap<String, DiscoveredDevice> = HashMap()

    private var location: Location? = null

    override suspend fun doWork(): Result {
        Timber.d("Bluetooth scanning worker started!")
        val scanMode = getScanMode()
        val scanId = scanRepository.insert(Scan(startDate = LocalDateTime.now(), isManual = false, scanMode = scanMode))

        if (!Util.checkBluetoothPermission()) {
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

        scanResultDictionary = HashMap()

        val useLocation = SharedPrefs.useLocationInTrackingDetection
        if (useLocation) {
            val lastLocation = locationProvider.getLastLocation()

            location = lastLocation
            Timber.d("Using last location for the tag detection")

            //Getting the most accurate location here
            locationProvider.getCurrentLocation { loc ->
                this.location = loc
                Timber.d("Updated to current location")
            }
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

        if (location == null) {
            Timber.d("No location found")
        }

        //Adding all scan results to the database after the scan has finished
        scanResultDictionary.forEach { (_, discoveredDevice) ->
            insertScanResult(
                discoveredDevice.scanResult,
                location?.latitude,
                location?.longitude,
                discoveredDevice.discoveryDate
            )
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
            if (!scanResultDictionary.containsKey(scanResult.device.address)) {
                Timber.d("Found ${scanResult.device.address} at ${LocalDateTime.now()}")
                scanResultDictionary[scanResult.device.address] =
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

    private suspend fun insertScanResult(
        scanResult: ScanResult,
        latitude: Double?,
        longitude: Double?,
        discoveryDate: LocalDateTime
    ) {
        var device = deviceRepository.getDevice(scanResult.device.address)
        if (device == null) {
            device = BaseDevice(scanResult)
            deviceRepository.insert(device)
        } else {
            Timber.d("Device already in the database... Updating the last seen date!")
            device.lastSeen = discoveryDate
            deviceRepository.update(device)
        }

        Timber.d("Device: $device")

        val uuids =  scanResult.scanRecord?.serviceUuids?.map { it.toString() }?.toList()
        val beacon = if (BuildConfig.DEBUG) {
            // Save the manufacturer data to the beacon
            Beacon(
                discoveryDate, scanResult.rssi, scanResult.device.address, latitude, longitude,
                scanResult.scanRecord?.bytes, uuids
            )
        } else {
            Beacon(
                discoveryDate, scanResult.rssi, scanResult.device.address, latitude, longitude,
                null, uuids
            )
        }

        beaconRepository.insert(beacon)
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
            15000L
        } else {
            8000L
        }
    }

    class DiscoveredDevice(var scanResult: ScanResult, var discoveryDate: LocalDateTime) {}
}